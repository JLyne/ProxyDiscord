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
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.Component;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.spongepowered.configurate.ConfigurationNode;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;
import uk.co.notnull.proxydiscord.api.logging.LogType;
import uk.co.notnull.proxydiscord.api.events.DiscordChatEvent;
import uk.co.notnull.proxydiscord.api.logging.LogVisibility;
import uk.co.notnull.proxydiscord.manager.LinkingManager;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class LoggingChannelHandler extends ListenerAdapter {
	private final ProxyDiscord plugin;
	private final ProxyServer proxy;
	private final Logger logger;
	private final LinkingManager linkingManager;

	private boolean logSentMessages = false;
	private boolean logIsPublic = true;
	private final AtomicReference<Integer> lockDummy = new AtomicReference<>(0);

	private LoggingFormatter formatter;
    private final Set<LogType> events = new HashSet<>();
    private final Set<RegisteredServer> servers = new HashSet<>();

	private final AtomicInteger logsPerMessage = new AtomicInteger(1); //Number of logs to combine together into one message, to avoid rate limits and falling behind
    private final AtomicInteger unsentLogs = new AtomicInteger(0); //Number of unsent logs in current message
    private final AtomicInteger queuedToSend = new AtomicInteger(0); //Number of messages waiting to be sent by jda
	private StringBuilder currentMessage = new StringBuilder();

	public static ConfigurationNode defaultConfig;
	private final StandardGuildMessageChannel loggingChannel;

	public LoggingChannelHandler(ProxyDiscord plugin, StandardGuildMessageChannel channel, ConfigurationNode config) {
		this.plugin = plugin;
		this.proxy = plugin.getProxy();
		this.logger = plugin.getLogger();
		this.linkingManager = plugin.getLinkingManager();
		this.loggingChannel = channel;

		parseConfig(config);

		String channelName = "#" + channel.getName();

		logger.info("Activity logging enabled for channel: {} (id: {}) in {}", channelName, channel.getId(),
					channel.getGuild().getName());

		if (!channel.canTalk()) {
			logger.warn("I don't have permission to send messages in {} (id: {}) in {}!", channelName, channel.getId(),
						channel.getGuild().getName());
		}

		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE)) {
			logger.warn("I don't have permission to manage messages in {} (id: {}) in {}!", channelName, channel.getId(),
						channel.getGuild().getName());
		}
	}

	private void parseConfig(ConfigurationNode config) {
		servers.clear();
		events.clear();

		ConfigurationNode eventList = config.node("events");
		eventList = eventList.empty() ? defaultConfig.node("events") : eventList;

		if(eventList.isList()) {
			eventList.childrenList().forEach((ConfigurationNode event) -> {
				try {
					LogType logType = LogType.valueOf(event.getString("").toUpperCase(Locale.ROOT)
															  .replace("-", "_"));
					events.add(logType);
				} catch(IllegalArgumentException e) {
					logger.warn("Ignoring unknown event type '{}'", event.getString(""));
				}
			});
		}

        logSentMessages = events.contains(LogType.DISCORD_CHAT);
		logIsPublic = config.node("public").getBoolean(defaultConfig.node("public").getBoolean(true));

        ConfigurationNode serverList = config.node("servers");
        serverList = serverList.empty() ? defaultConfig.node("servers") : serverList;

        if(serverList.isList()) {
        	serverList.childrenList().forEach((ConfigurationNode key) -> {
        		Optional<RegisteredServer> server = proxy.getServer(key.getString(""));

        		if(server.isEmpty()) {
					logger.warn("Ignoring unknown server '{}'", key.getString(""));
        			return;
				}

        		servers.add(server.get());
			});
		}

        this.formatter = new LoggingFormatter(config.node("formats"), defaultConfig.node("formats"));
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

		if(!formatter.hasFormat(entry.getType())) {
			return false;
		}

		if(!servers.isEmpty() && entry.getServer().isEmpty()) {
			return false;
		}

		return servers.isEmpty() || servers.contains(entry.getServer().get());
	}

	public void updateLogsPerMessage() {
		//Decrease logs per message if a low number of messages are unsent
		if(queuedToSend.get() <= 2 && logsPerMessage.get() > 1) {
			logger.info("Decreasing logsPerMessage due to low activity ({} queued messages)", queuedToSend.get());
			logsPerMessage.set(Math.max(logsPerMessage.get() / 2, 1));
		}
	}

    public void logEvent(LogEntry entry) {
		if(shouldLogEvent(entry)) {
			queueLogMessage(formatter.formatLogEntry(entry));
		}
	}

    private void queueLogMessage(String message) {
        if(loggingChannel == null || message.isEmpty()) {
        	return;
        }

        synchronized (lockDummy) {
			// Ensure <2000 characters
			message = formatter.truncateMessage(message);

			// If message is too long to append, send existing message and try again
			if(!formatter.appendMessage(currentMessage, message)) {
				sendLogMessage(loggingChannel);
				formatter.appendMessage(currentMessage, message);
			}

            if(unsentLogs.incrementAndGet() >= logsPerMessage.get()) {
            	sendLogMessage(loggingChannel);
            }

            //Increase logsPerMessage if many messages are queued, to help stop the log falling behind
            if(queuedToSend.get() >= 5 && logsPerMessage.get() < 16) {
				logger.info("Increasing logsPerMessage due to high activity ({} queued messages)", queuedToSend.get());
                logsPerMessage.set(Math.min(logsPerMessage.get() * 2, 16));
            }
        }
    }

    private void sendLogMessage(StandardGuildMessageChannel channel) {
		queuedToSend.incrementAndGet();
		channel.sendMessage(currentMessage).submit()
				.thenAcceptAsync(_ -> queuedToSend.decrementAndGet())
				.exceptionally(error -> {
					logger.warn("Failed to send log message: {}", error.getMessage());
					queuedToSend.decrementAndGet();
					return null;
				});

		currentMessage = new StringBuilder();
		unsentLogs.set(0);
	}

    public void onMessageReceived(MessageReceivedEvent event) {
		if (!event.isFromGuild() || !event.getGuildChannel().equals(loggingChannel)) {
			return;
		}

		String channelName = "#" + event.getChannel().getName();
		String ignoreMessage = "Ignoring message from " + event.getAuthor().getName() + " in " + channelName + ": ";

		if(event.getAuthor().equals(event.getJDA().getSelfUser())) {
			return;
		}

		if(event.getAuthor().isBot() || event.getAuthor().isSystem()) {
			plugin.getDebugLogger().info(ignoreMessage + "sent by bot user");
			return;
		}

		if(logSentMessages && event.getGuild().getSelfMember().hasPermission(loggingChannel, Permission.MESSAGE_MANAGE)) {
			event.getMessage().delete().queue();
		}

		Long discordId = event.getAuthor().getIdLong();
		UUID linked = linkingManager.getLinked(discordId);

		if(linked == null) {
			plugin.getDebugLogger().info(ignoreMessage + "sent by unlinked user");
			return;
		}

		Message message = event.getMessage();
		String content = message.getContentDisplay();

		if(content.isEmpty() && message.getAttachments().isEmpty()) {
			plugin.getDebugLogger().info(ignoreMessage + "empty message");
			return;
		}

		proxy.getScheduler().buildTask(plugin, () -> {
			UserManager userManager = plugin.getLuckpermsManager().getUserManager();
			User user = userManager.loadUser(linked).join();

			if(user == null) {
				plugin.getDebugLogger().info(ignoreMessage + "unable to find matching luckperms user");
				return;
			}

			proxy.getEventManager().fire(new DiscordChatEvent(user, content, new HashSet<>(servers))).thenAccept(e -> {
				if (!e.getResult().isAllowed()) {
					if(!logSentMessages && event.getGuild().getSelfMember().hasPermission(loggingChannel, Permission.MESSAGE_MANAGE)) {
						message.delete().queue();
					}

					plugin.getDebugLogger().info(ignoreMessage + "DiscordChatEvent got denied result");
					return;
				}

				handleDiscordMessage(message, e);
			}).exceptionally(e -> {
				logger.warn("Failed to handle discord message: {}", e.getMessage());
				return null;
			});
		}).schedule();
	}

    private void handleDiscordMessage(Message message, DiscordChatEvent event) {
		User user = event.getSender();
		String messageContent = event.getResult().getMessage().orElse(event.getMessageContent());
		Set<RegisteredServer> servers = event.getServers();

		if(logSentMessages) {
			try {
				queueLogMessage(formatter.formatDiscordMessageLog(user, message, messageContent));
			} catch (IllegalStateException e) {
				logger.warn("Failed to send Discord message: {}", e.getMessage());
			}
		}

		Component messageComponent = formatter.formatDiscordMessageIngame(user, message, messageContent);

		proxy.getAllPlayers().forEach(player -> {
			RegisteredServer server = player.getCurrentServer().map(ServerConnection::getServer).orElse(null);

			if (servers.isEmpty() || servers.contains(server)) {
				player.sendMessage(messageComponent);
			}
		});
    }

	public void update(ConfigurationNode config) {
		parseConfig(config);
	}
}
