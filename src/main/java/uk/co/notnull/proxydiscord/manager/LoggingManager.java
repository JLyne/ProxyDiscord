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

package uk.co.notnull.proxydiscord.manager;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import org.spongepowered.configurate.ConfigurationNode;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.api.events.DiscordLogEvent;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;
import uk.co.notnull.proxydiscord.api.logging.LogType;
import uk.co.notnull.proxydiscord.api.logging.LogVisibility;
import uk.co.notnull.proxydiscord.logging.LoggingChannelHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LoggingManager implements uk.co.notnull.proxydiscord.api.manager.LoggingManager {
    private final ProxyDiscord plugin;
	private final ProxyServer proxy;
    private final Logger logger;
    private final Map<Guild, Map<StandardGuildMessageChannel, LoggingChannelHandler>> handlers;
	private ConfigurationNode config;

	private boolean useChatEvent = true;
	private boolean useCommandEvent = true;
	private boolean useConnectEvent = true;
	private boolean useDisconnectEvent = true;

	public LoggingManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();

        this.handlers = new HashMap<>();
		this.config = config;

		parseConfig();
    }

    public void init() {
		//Can't schedule tasks until ProxyInitializeEvent
        proxy.getScheduler().buildTask(plugin, () ->
				handlers.forEach((_, guildHandlers)
										 -> guildHandlers.values().forEach(LoggingChannelHandler::updateLogsPerMessage)))
				.repeat(5, TimeUnit.SECONDS)
				.delay(5, TimeUnit.SECONDS)
				.schedule();
	}

    private void parseConfig() {
		useChatEvent = config.node("events", "use-chat-event").getBoolean(true);
		useCommandEvent = config.node("events", "use-command-event").getBoolean(true);
		useConnectEvent = config.node("events", "use-connect-event").getBoolean(true);
		useDisconnectEvent = config.node("events", "use-disconnect-event").getBoolean(true);

        LoggingChannelHandler.defaultConfig = config.node("logging", "default");
    }

	public void findChannels(Guild guild) {
		removeChannels(guild);
		Map<StandardGuildMessageChannel, LoggingChannelHandler> newHandlers = new ConcurrentHashMap<>();

		 config.node("logging").childrenMap().forEach((Object key, ConfigurationNode channelConfig) -> {
			 if(key.toString().equals("default")) {
                return;
			 }

			 String channelId = key.toString();

			 try {
				 StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, channelId);

				 if (channel == null) {
					 return;
				 }

				 LoggingChannelHandler handler = new LoggingChannelHandler(plugin, channel, channelConfig);
				 newHandlers.put(channel, handler);

				 guild.getJDA().addEventListener(handler);
				 plugin.getProxy().getEventManager().register(plugin, handler);
			 } catch(RuntimeException e) {
				 logger.warn("Unable to add handler for announcement channel {}", channelId, e);
			 }
		});

		handlers.put(guild, newHandlers);
	}

	public void removeChannels(Guild guild) {
		handlers.computeIfPresent(guild, (_, value) -> {
			for (LoggingChannelHandler handler : value.values()) {
				guild.getJDA().removeEventListener(handler);
				plugin.getProxy().getEventManager().unregisterListener(plugin, handler);
			}

			return null;
		});
	}

	// Listen on high priority to run before VanishBridge updates the vanish state
	// This allows us to log leave messages if a player leaves unvanished to join another server vanished
	@Subscribe(priority = Short.MAX_VALUE - 1)
	public void onServerLeave(ServerPostConnectEvent event) {
		if(!useConnectEvent) {
			return;
		}

		Player player = event.getPlayer();
		boolean privateLog = plugin.getVanishBridgeHelper().isVanished(player);

		if(event.getPreviousServer() != null) {
			LogEntry leaveLog = LogEntry.builder().type(LogType.LEAVE).player(player).server(event.getPreviousServer())
					.visibility(privateLog ? LogVisibility.PRIVATE_ONLY : LogVisibility.UNSPECIFIED).build();

			logEvent(leaveLog);
		}
	}

	// Listen on low priority to run before VanishBridge updates the vanish state
	// This allows us to suppress join messages if a player leaves unvanished to join another server vanished
	@Subscribe(priority = Short.MIN_VALUE + 1)
	public void onServerJoin(ServerPostConnectEvent event) {
		if(!useConnectEvent) {
			return;
		}

		Player player = event.getPlayer();
		boolean privateLog = plugin.getVanishBridgeHelper().isVanished(player);
		LogEntry joinLog = LogEntry.builder().type(LogType.JOIN).player(player)
				.visibility(privateLog ? LogVisibility.PRIVATE_ONLY : LogVisibility.UNSPECIFIED).build();

		logEvent(joinLog);
	}

	@Subscribe
	public void onDisconnect(DisconnectEvent event) {
		if(!useDisconnectEvent) {
			return;
		}

		Player player = event.getPlayer();
		boolean privateLog = plugin.getVanishBridgeHelper().isVanished(player);
		LogEntry leaveLog = LogEntry.builder().type(LogType.LEAVE).player(player)
				.visibility(privateLog ? LogVisibility.PRIVATE_ONLY : LogVisibility.UNSPECIFIED).build();

		logEvent(leaveLog);
	}

    @Subscribe(priority = Short.MIN_VALUE + 1)
    public void onPlayerChat(PlayerChatEvent event) {
        if(!useChatEvent || !event.getResult().isAllowed()) {
            return;
        }

        LogEntry chatLog = LogEntry.builder().type(LogType.CHAT).player(event.getPlayer()).replacements(
				Map.of("message", event.getMessage())).build();

        logEvent(chatLog);
    }

    @Subscribe(priority = Short.MIN_VALUE / 2)
    public void onPlayerCommand(CommandExecuteEvent event) {
		if(!useCommandEvent || !event.getResult().isAllowed() || !(event.getCommandSource() instanceof Player)) {
            return;
        }

        LogEntry commandLog = LogEntry.builder().type(LogType.COMMAND).player((Player) event.getCommandSource())
				.replacements(Map.of("command", event.getCommand())).build();

        logEvent(commandLog);
    }

    @Override
    public CompletableFuture<Void> logEvent(LogEntry entry) {
    	return proxy.getEventManager().fire(new DiscordLogEvent(entry)).thenAccept(event -> {
			if (!event.getResult().isAllowed()) {
				return;
			}

			LogEntry newEntry = entry.toBuilder().visibility(event.getResult().getVisibility()).build();
			logCustomEvent(newEntry);
		});
	}

    @Override
    public void logCustomEvent(LogEntry entry) {
		for (Map<StandardGuildMessageChannel, LoggingChannelHandler> handlers : handlers.values()) {
			for (LoggingChannelHandler handler : handlers.values()) {
				handler.logEvent(entry);
			}
		}
    }

    public void reload(ConfigurationNode config) {
		this.config = config;
		parseConfig();

        for (Guild guild : plugin.getDiscord().getJDA().getGuilds()) {
            findChannels(guild);
        }
    }
}
