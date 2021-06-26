package uk.co.notnull.proxydiscord.logging;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
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
import uk.co.notnull.proxydiscord.events.DiscordChatEvent;
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

		proxy.getEventManager().register(plugin, this);

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

        logger.info("Activity logging enabled for channel: #" + loggingChannel.toString().replaceAll(".*\\[|].*", "") + " (id: " + channelId + ")");
    }

    private boolean shouldLogEvent(LogType type, Player player) {
		return shouldLogEvent(type, player.getCurrentServer().map(ServerConnection::getServer)
				.orElse(null));
	}

    private boolean shouldLogEvent(LogType type, RegisteredServer server) {
		if(!events.isEmpty() && !events.contains(type)) {
			return false;
		}

		if(formats.getOrDefault(type, "").isEmpty()) {
			return false;
		}

		if(!servers.isEmpty() && server == null) {
			return false;
		}

		if(!servers.isEmpty() && !servers.contains(server)) {
			return false;
		}

		return true;
	}

    @Subscribe(order = PostOrder.LAST)
	public void onServerPostConnect(ServerPostConnectEvent event) {
		Player player = event.getPlayer();

		if(shouldLogEvent(LogType.JOIN, player)) {
			sendLogMessage(LogType.JOIN, player, Collections.emptyMap());
		}

		if(event.getPreviousServer() != null && shouldLogEvent(LogType.LEAVE, event.getPreviousServer())) {
			sendLogMessage(LogType.LEAVE, player,
						   Map.of("[server]", Util.escapeMarkdown(event.getPreviousServer()
																		  .getServerInfo().getName())));
		}
	}

	@Subscribe(order = PostOrder.LAST)
	public void onDisconnect(DisconnectEvent event) {
		Player player = event.getPlayer();

		if(shouldLogEvent(LogType.LEAVE, player)) {
			sendLogMessage(LogType.LEAVE, player, Collections.emptyMap());
		}
	}

    @Subscribe(order = PostOrder.LATE)
    public void onPlayerChat(PlayerChatEvent event) {
        if(!event.getResult().isAllowed() || !shouldLogEvent(LogType.CHAT, event.getPlayer())) {
            return;
        }

        String message = Util.escapeMarkdown(Util.stripFormatting(event.getMessage()));

        sendLogMessage(LogType.CHAT, event.getPlayer(), Map.of("[message]", message));
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPlayerCommand(CommandExecuteEvent event) {
        if(!event.getResult().isAllowed() || !(event.getCommandSource() instanceof Player)
				|| !shouldLogEvent(LogType.COMMAND, (Player) event.getCommandSource())) {
            return;
        }

        sendLogMessage(LogType.COMMAND, (Player) event.getCommandSource(),
					   Map.of("[command]", Util.escapeMarkdown(event.getCommand())));
    }

    private void sendLogMessage(LogType type, Player player, Map<String, String> replacements) {
		var ref = new Object() {
			String message = formats.get(type);
		};

		if(ref.message.isEmpty()) {
			return;
		}

        Long discordId = linkingManager.getLinked(player);
        String serverName = player.getCurrentServer().
				map(connection -> connection.getServerInfo().getName()).orElse("unknown");

        replacements.forEach((String find, String replace) -> ref.message = ref.message.replace(find, replace));

        ref.message = ref.message.replace("[server]", Util.escapeMarkdown(serverName));
        ref.message = ref.message.replace("[player]", Util.escapeMarkdown(player.getUsername()));
        ref.message = ref.message.replace("[discord_id]", discordId != null ? String.valueOf(discordId) : "Unlinked");
        ref.message = ref.message.replace("[discord_mention]", discordId != null ? "<@!" + discordId + ">" : "");

        sendLogMessage(ref.message);
    }

    private void sendLogMessage(LogType type, User user, Map<String, String> replacements) {
		var ref = new Object() {
			String message = formats.get(type);
		};

		if(ref.message.isEmpty()) {
			return;
		}

        Long discordId = linkingManager.getLinked(user.getUniqueId());

        replacements.forEach((String find, String replace) -> ref.message = ref.message.replace(find, replace));

        ref.message = ref.message.replace("[server]", "");
        ref.message = ref.message.replace("[player]", user.getFriendlyName());
        ref.message = ref.message.replace("[discord_id]", discordId != null ? String.valueOf(discordId) : "Unlinked");
        ref.message = ref.message.replace("[discord_mention]", discordId != null ? "<@!" + discordId + ">" : "");

        sendLogMessage(ref.message);
    }

    private void sendLogMessage(String message) {
        message = message.replace("[date]", dateFormat != null ?
				Util.escapeMarkdown(dateFormat.format(new Date())) : "");

        Optional <TextChannel> loggingChannel = plugin.getDiscord().getApi().getTextChannelById(channelId);

        if(loggingChannel.isEmpty() || message.isEmpty()) {
        	return;
        }

        synchronized (lockDummy) {
            if(currentMessage.getStringBuilder().length() + message.length() > 1950) {

                queuedToSend.incrementAndGet();
                currentMessage.setAllowedMentions(allowedMentions);
                currentMessage.send(loggingChannel.get())
                        .thenAcceptAsync(result -> queuedToSend.decrementAndGet()).exceptionally(error -> {
                    logger.warn("Failed to send log message");
                    queuedToSend.decrementAndGet();
                    return null;
                });

                currentMessage = new MessageBuilder();
                unsentLogs.set(0);
            }

            currentMessage.append(message).append("\n");

            if(unsentLogs.incrementAndGet() >= logsPerMessage.get()) {

                queuedToSend.incrementAndGet();
                currentMessage.setAllowedMentions(allowedMentions);
                currentMessage.send(loggingChannel.get())
                        .thenAcceptAsync(result -> queuedToSend.decrementAndGet()).exceptionally(error -> {
                    logger.warn("Failed to send log message");
                    queuedToSend.decrementAndGet();
                    return null;
                });

                currentMessage = new MessageBuilder();
                unsentLogs.set(0);
            }

            //Increase logsPerMessage if many messages are queued, to help stop the log falling behind
            if(queuedToSend.get() >= 5 && logsPerMessage.get() < 16) {
                logger.info("Increasing logsPerMessage due to high activity (" + queuedToSend.get() + " queued messages)");
                logsPerMessage.set(Math.min(logsPerMessage.get() * 2, 16));
            }
        }
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

			logger.info(Thread.currentThread().getName());

			handleDiscordMessage(user, message);
		}).schedule();
	}

    private void handleDiscordMessage(User user, String message) {
		if(ingameChatFormat.isEmpty()) {
			return;
		}

		proxy.getEventManager().fire(new DiscordChatEvent(user, message, new HashSet<>(servers))).thenAccept(event -> {
			if(!event.getResult().isAllowed()) {
				return;
			}

			if(logSentMessages) {
				try {
					sendLogMessage(LogType.DISCORD_CHAT, user, Map.of("[message]", message));
				} catch (IllegalStateException e) {
					logger.warn("Failed to send Discord message: " + e.getMessage());
				}
			}

			Set<RegisteredServer> servers = event.getServers();
			CachedMetaData metaData = user.getCachedData().getMetaData(QueryOptions.nonContextual());
			String prefix = ( metaData.getPrefix() != null) ?  metaData.getPrefix() : "";
			String suffix = ( metaData.getSuffix() != null) ?  metaData.getSuffix() : "";

			Component messageComponent = Util.legacySerializer
					.deserialize(ingameChatFormat
										 .replace("[prefix]", prefix)
										 .replace("[suffix]", suffix)
										 .replace("[player]", user.getFriendlyName())
										 .replace("[message]", Util.stripFormatting(event.getResult().getMessage()
																							.orElse(event.getMessage()))));

			proxy.getAllPlayers().forEach(player -> {
				RegisteredServer server = player.getCurrentServer().map(ServerConnection::getServer).orElse(null);

				if (servers.isEmpty() || servers.contains(server)) {
					player.sendMessage(Identity.identity(user.getUniqueId()), messageComponent);
				}
			});
		}).exceptionally(e -> {
			logger.warn("Failed to handle discord message: " + e.getMessage());
			return null;
		});
    }

    public void remove() {
		logListener.remove();
		proxy.getEventManager().unregisterListener(ProxyDiscord.inst(), this);
	}

	public void update(ConfigurationNode config) {
		parseConfig(config);
	}
}
