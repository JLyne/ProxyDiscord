/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2021 James Lyne
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package uk.co.notnull.proxydiscord.logging;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import ninja.leaping.configurate.ConfigurationNode;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.mention.AllowedMentions;
import org.javacord.api.entity.message.mention.AllowedMentionsBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.Util;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;
import uk.co.notnull.proxydiscord.api.logging.LogType;
import uk.co.notnull.proxydiscord.api.events.DiscordChatEvent;
import uk.co.notnull.proxydiscord.api.logging.LogVisibility;
import uk.co.notnull.proxydiscord.manager.LinkingManager;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggingChannelHandler {
	private final ProxyDiscord plugin;
	private final ProxyServer proxy;
	private final Logger logger;
	private final LinkingManager linkingManager;

	private final long channelId;
	private boolean logSentMessages = false;
	private boolean logIsPublic = true;
	private final Integer lockDummy = 0;

	private SimpleDateFormat dateFormat;
	private String ingameChatFormat;
    private final Set<LogType> events = new HashSet<>();
	private final Map<LogType, String> formats = new HashMap<>();
    private final Set<RegisteredServer> servers = new HashSet<>();

    private ListenerManager<MessageCreateListener> logListener;
	private final AtomicInteger logsPerMessage = new AtomicInteger(1); //Number of logs to combine together into one message, to avoid rate limits and falling behind
    private final AtomicInteger unsentLogs = new AtomicInteger(0); //Number of unsent logs in current message
    private final AtomicInteger queuedToSend = new AtomicInteger(0); //Number of messages waiting to be sent by javacord
	private MessageBuilder currentMessage = new MessageBuilder();

	public static ConfigurationNode defaultConfig;
	private static final AllowedMentions allowedMentions;

	static {
		AllowedMentionsBuilder allowedMentionsBuilder = new AllowedMentionsBuilder();
		allowedMentionsBuilder.setMentionRoles(false).setMentionUsers(false).setMentionEveryoneAndHere(false);
        allowedMentions = allowedMentionsBuilder.build();
	}

	public LoggingChannelHandler(ProxyDiscord plugin, long channelId, ConfigurationNode config) {
		this.plugin = plugin;
		this.proxy = plugin.getProxy();
		this.logger = plugin.getLogger();
		this.linkingManager = plugin.getLinkingManager();
		this.channelId = channelId;

		//Decrease logs per message if a low number of messages are unsent
        proxy.getScheduler().buildTask(plugin, () -> {
            if(queuedToSend.get() <= 2 && logsPerMessage.get() > 1) {
                logger.info("Decreasing logsPerMessage due to low activity (" + queuedToSend.get() + " queued messages)");
                logsPerMessage.set(Math.max(logsPerMessage.get() / 2, 1));
            }
        }).repeat(5, TimeUnit.SECONDS).delay(5, TimeUnit.SECONDS).schedule();

		plugin.getDiscord().getApi().addReconnectListener(event -> findChannel());
		parseConfig(config);
	}

	private void parseConfig(ConfigurationNode config) {
		servers.clear();
		events.clear();

		ConfigurationNode eventList = config.getNode("events");
		eventList = eventList.isEmpty() ? defaultConfig.getNode("events") : eventList;

		if(eventList.isList()) {
			eventList.getChildrenList().forEach((ConfigurationNode event) -> {
				try {
					LogType logType = LogType.valueOf(event.getString("").toUpperCase(Locale.ROOT)
															  .replace("-", "_"));
					events.add(logType);
				} catch(IllegalArgumentException e) {
					logger.warn("Ignoring unknown event type '" + event.getString("") + "'");
				}
			});
		}

        logSentMessages = events.contains(LogType.DISCORD_CHAT);
		logIsPublic = config.getNode("public").getBoolean(defaultConfig.getNode("public").getBoolean(true));

        ConfigurationNode serverList = config.getNode("servers");
        serverList = serverList.isEmpty() ? defaultConfig.getNode("servers") : serverList;

        if(serverList.isList()) {
        	serverList.getChildrenList().forEach((ConfigurationNode key) -> {
        		Optional<RegisteredServer> server = proxy.getServer(key.getString(""));

        		if(server.isEmpty()) {
        			logger.warn("Ignoring unknown server '" + key.getString("") + "'");
        			return;
				}

        		servers.add(server.get());
			});
		}

        ConfigurationNode logFormats = config.getNode("formats");
        ConfigurationNode defaultLogFormats = defaultConfig.getNode("formats");

        if(logFormats.isMap() || defaultLogFormats.isMap()) {
            ConfigurationNode dateFormat = logFormats.getNode("date");
            ConfigurationNode chatFormat = logFormats.getNode("chat");
            ConfigurationNode discordChatFormat = logFormats.getNode("discord-chat");
            ConfigurationNode joinFormat = logFormats.getNode("join");
            ConfigurationNode leaveFormat = logFormats.getNode("leave");
            ConfigurationNode commandFormat = logFormats.getNode("command");
            ConfigurationNode ingameChatFormat = logFormats.getNode("discord-chat-ingame");

            ConfigurationNode defaultDateFormat = defaultLogFormats.getNode("date");
            ConfigurationNode defaultChatFormat = defaultLogFormats.getNode("chat");
            ConfigurationNode defaultDiscordChatFormat = defaultLogFormats.getNode("discord-chat");
            ConfigurationNode defaultJoinFormat = defaultLogFormats.getNode("join");
            ConfigurationNode defaultLeaveFormat = defaultLogFormats.getNode("leave");
            ConfigurationNode defaultCommandFormat = defaultLogFormats.getNode("command");
            ConfigurationNode defaultIngameChatFormat = defaultLogFormats.getNode("discord-chat-ingame");

            try {
                this.dateFormat = new SimpleDateFormat(dateFormat.getString(defaultDateFormat.getString("")));
            } catch(IllegalArgumentException e) {
                logger.warn("Invalid logging date format: " + e.getMessage());
            }

            this.formats.put(LogType.CHAT, chatFormat.getString(defaultChatFormat.getString("")));
            this.formats.put(LogType.DISCORD_CHAT, discordChatFormat.getString(defaultDiscordChatFormat.getString("")));
            this.formats.put(LogType.JOIN, joinFormat.getString(defaultJoinFormat.getString("")));
            this.formats.put(LogType.LEAVE, leaveFormat.getString(defaultLeaveFormat.getString("")));
            this.formats.put(LogType.COMMAND, commandFormat.getString(defaultCommandFormat.getString("")));
            this.ingameChatFormat = ingameChatFormat.getString(defaultIngameChatFormat.getString(""));
        }

        findChannel();
	}

	private void findChannel() {
        Optional<TextChannel> loggingChannel = plugin.getDiscord().getApi().getTextChannelById(channelId);

        if(loggingChannel.isEmpty()) {
            logger.warn("Unable to find logging channel. Did you put a valid channel ID in the config?");
            return;
        }

        if(logListener != null) {
            logListener.remove();
        }

        logListener = loggingChannel.get().addMessageCreateListener(this::handleDiscordMessageEvent);
        String channelName = "#" + loggingChannel.toString().replaceAll(".*\\[|].*", "");

        logger.info("Activity logging enabled for channel: " + channelName + " (id: " + channelId + ")");

        loggingChannel.ifPresent(channel -> {
            if(!channel.canYouWrite()) {
                logger.warn("I don't have permission to send messages in " + channelName + " (id: " +
									channel.getIdAsString() + ")!");
            }

            if(!channel.canYouManageMessages()) {
                logger.warn("I don't have permission to manage messages in " + channelName + " (id: " +
									channel.getIdAsString() + ")!");
            }
        });
    }

    private boolean shouldLogEvent(LogEntry entry) {
		if(!events.isEmpty() && !events.contains(entry.getType())) {
			return false;
		}

		if(logIsPublic && entry.getVisibility() == LogVisibility.PRIVATE_ONLY) {
			return false;
		}

		if(!logIsPublic && entry.getVisibility() == LogVisibility.PUBLIC_ONLY) {
			return false;
		}

		if(formats.getOrDefault(entry.getType(), "").isEmpty()) {
			return false;
		}

		if(!servers.isEmpty() && entry.getServer().isEmpty()) {
			return false;
		}

		return servers.isEmpty() || servers.contains(entry.getServer().get());
	}

    public void logEvent(LogEntry entry) {
		if(shouldLogEvent(entry)) {
			queueLogMessage(Util.formatLogEntry(formats.get(entry.getType()), entry));
		}
	}

    private void queueLogMessage(String message) {
        message = message.replace("[date]", dateFormat != null ?
				Util.escapeFormatting(dateFormat.format(new Date())) : "");

        Optional <TextChannel> loggingChannel = plugin.getDiscord().getApi().getTextChannelById(channelId);

        if(loggingChannel.isEmpty() || message.isEmpty()) {
        	return;
        }

        synchronized (lockDummy) {
        	int currentLength  = currentMessage.getStringBuilder().length();

        	if(message.length() > 2000) {
        		message = message.substring(0, 1991) + "[...]";

        		if(message.startsWith("```")) {
        			message = message + "```";
				}
			}

            if(currentLength > 0 && currentLength + message.length() > 2000) {
            	sendLogMessage(loggingChannel.get());
            }

            if(currentLength == 0) {
            	currentMessage.append("\n");
			}

            currentMessage.append(message);

            if(unsentLogs.incrementAndGet() >= logsPerMessage.get()) {
            	sendLogMessage(loggingChannel.get());
            }

            //Increase logsPerMessage if many messages are queued, to help stop the log falling behind
            if(queuedToSend.get() >= 5 && logsPerMessage.get() < 16) {
                logger.info("Increasing logsPerMessage due to high activity (" + queuedToSend.get() + " queued messages)");
                logsPerMessage.set(Math.min(logsPerMessage.get() * 2, 16));
            }
        }
    }

    private void sendLogMessage(TextChannel channel) {
		queuedToSend.incrementAndGet();
		currentMessage.setAllowedMentions(allowedMentions);
		currentMessage.send(channel).thenAcceptAsync(result -> queuedToSend.decrementAndGet()).exceptionally(error -> {
			logger.warn("Failed to send log message: " + error.getMessage());
			queuedToSend.decrementAndGet();
			return null;
		});

		currentMessage = new MessageBuilder();
		unsentLogs.set(0);
	}

    private void handleDiscordMessageEvent(MessageCreateEvent event) {
		if(event.getMessageAuthor().isYourself() || !event.getMessageAuthor().isRegularUser()) {
			return;
		}

		if(logSentMessages) {
			event.deleteMessage();
		}

		Long discordId = event.getMessageAuthor().getId();
		UUID linked = linkingManager.getLinked(discordId);

		if(linked == null) {
			return;
		}

		String message = Util.getDiscordMessageContent(event.getMessage());

		if(message.isEmpty()) {
			return;
		}

		proxy.getScheduler().buildTask(plugin, () -> {
			UserManager userManager = plugin.getLuckpermsManager().getUserManager();
			User user = userManager.loadUser(linked).join();

			if(user == null) {
				return;
			}

			proxy.getEventManager().fire(new DiscordChatEvent(user, message, new HashSet<>(servers))).thenAccept(e -> {
				if (!e.getResult().isAllowed() && !logSentMessages) {
					event.deleteMessage();
					return;
				}

				handleDiscordMessage(user, e.getResult().getMessage().orElse(e.getMessage()), e.getServers());
			}).exceptionally(e -> {
				logger.warn("Failed to handle discord message: " + e.getMessage());
				return null;
			});
		}).schedule();
	}

    private void handleDiscordMessage(User user, String message, Set<RegisteredServer> servers) {
		if(ingameChatFormat.isEmpty()) {
			return;
		}

		if(logSentMessages) {
			try {
				String sanitised = Util.plainSerializer.serialize(Util.plainSerializer.deserialize(message));
				String formatted = Util.formatDiscordMessage(formats.get(LogType.DISCORD_CHAT),
															 user, Map.of("[message]", sanitised));
				queueLogMessage(formatted);
			} catch (IllegalStateException e) {
				logger.warn("Failed to send Discord message: " + e.getMessage());
			}
		}

		CachedMetaData metaData = user.getCachedData().getMetaData(QueryOptions.nonContextual());
		String prefix = ( metaData.getPrefix() != null) ?  metaData.getPrefix() : "";
		String suffix = ( metaData.getSuffix() != null) ?  metaData.getSuffix() : "";
		message = Util.stripSectionFormatting(message);

		logger.info(message);

		Component messageComponent = Util.legacySerializer
				.deserialize(ingameChatFormat
									 .replace("[prefix]", prefix)
									 .replace("[suffix]", suffix)
									 .replace("[player]", user.getFriendlyName()))
				.replaceText(TextReplacementConfig.builder().matchLiteral("[message]")
											 .replacement(message).build());

		proxy.getAllPlayers().forEach(player -> {
			RegisteredServer server = player.getCurrentServer().map(ServerConnection::getServer).orElse(null);

			if (servers.isEmpty() || servers.contains(server)) {
				player.sendMessage(Identity.identity(user.getUniqueId()), messageComponent);
			}
		});
    }

    public void remove() {
		logListener.remove();
	}

	public void update(ConfigurationNode config) {
		parseConfig(config);
	}
}
