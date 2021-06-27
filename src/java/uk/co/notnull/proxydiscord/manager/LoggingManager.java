package uk.co.notnull.proxydiscord.manager;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.Util;
import uk.co.notnull.proxydiscord.api.events.DiscordLogEvent;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;
import uk.co.notnull.proxydiscord.api.logging.LogType;
import uk.co.notnull.proxydiscord.logging.LoggingChannelHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class LoggingManager implements uk.co.notnull.proxydiscord.api.manager.LoggingManager {
    private final ProxyDiscord plugin;
	private final ProxyServer proxy;
    private final Logger logger;
    private final Map<Long, LoggingChannelHandler> handlers;

	public LoggingManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();

        this.handlers = new HashMap<>();

        plugin.getProxy().getEventManager().register(plugin, this);

        parseConfig(config);
    }

    private void parseConfig(ConfigurationNode config) {
        Set<Long> existing = new HashSet<>(handlers.keySet());

        LoggingChannelHandler.defaultConfig = config.getNode("logging", "default");

        config.getNode("logging").getChildrenMap().forEach((Object key, ConfigurationNode channelConfig) -> {
            if(key.toString().equals("default")) {
                return;
            }

            long channelId;

            try {
                channelId = Long.parseLong(key.toString());
            } catch(NumberFormatException e) {
                logger.warn("Ignoring logging channel '" + key + "': Invalid channel ID");
                return;
            }

            if(existing.contains(channelId)) {
                handlers.get(channelId).update(channelConfig);
                existing.remove(channelId);
            } else if(!handlers.containsKey(channelId)) {
                handlers.put(channelId, new LoggingChannelHandler(plugin, channelId, channelConfig));
            }
        });

        existing.forEach((Long channelId) -> {
            handlers.get(channelId).remove();
            handlers.remove(channelId);
        });
    }

    @SuppressWarnings("UnstableApiUsage")
	@Subscribe(order = PostOrder.LAST)
	public void onServerPostConnect(ServerPostConnectEvent event) {
		Player player = event.getPlayer();
		LogEntry joinLog = LogEntry.builder().type(LogType.JOIN).player(player).build();

		logEvent(joinLog);

		if(event.getPreviousServer() != null) {
			LogEntry leaveLog = LogEntry.builder().type(LogType.LEAVE).player(player).server(event.getPreviousServer())
					.build();

			logEvent(leaveLog);
		}
	}

	@Subscribe(order = PostOrder.LAST)
	public void onDisconnect(DisconnectEvent event) {
		Player player = event.getPlayer();
		LogEntry leaveLog = LogEntry.builder().type(LogType.LEAVE).player(player).build();

		logEvent(leaveLog);
	}

    @Subscribe(order = PostOrder.LAST)
    public void onPlayerChat(PlayerChatEvent event) {
        if(!event.getResult().isAllowed()) {
            return;
        }

		String message = Util.escapeMarkdown(Util.stripFormatting(event.getMessage()));
        LogEntry leaveLog = LogEntry.builder().type(LogType.CHAT).player(event.getPlayer())
				.replacements(Map.of("[message]", message)).build();

        logEvent(leaveLog);
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPlayerCommand(CommandExecuteEvent event) {
		if(!event.getResult().isAllowed() || !(event.getCommandSource() instanceof Player)) {
            return;
        }

		String command = Util.escapeMarkdown(Util.stripFormatting(event.getCommand()));
        LogEntry leaveLog = LogEntry.builder().type(LogType.COMMAND).player((Player) event.getCommandSource())
				.replacements(Map.of("[command]", command)).build();

        logEvent(leaveLog);
    }

    @Override
    public CompletableFuture<Void> logEvent(LogEntry entry) {
    	return proxy.getEventManager().fire(new DiscordLogEvent(entry)).thenAccept(event -> {
    		if(!event.getResult().isAllowed()) {
				return;
			}

    		LogEntry newEntry = entry.toBuilder().visibility(event.getResult().getVisibility()).build();
    		handlers.forEach((channelId, handler) -> handler.logEvent(newEntry));
		});
	}

    @Override
    public void logCustomEvent(LogEntry entry) {
        handlers.forEach((channelId, handler) -> handler.logEvent(entry));
    }

    public void reload(ConfigurationNode config) {
        parseConfig(config);
    }
}
