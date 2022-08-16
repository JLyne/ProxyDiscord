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
import uk.co.notnull.proxydiscord.api.events.DiscordLogEvent;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;
import uk.co.notnull.proxydiscord.api.logging.LogType;
import uk.co.notnull.proxydiscord.logging.LoggingChannelHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class LoggingManager implements uk.co.notnull.proxydiscord.api.manager.LoggingManager {
    private final ProxyDiscord plugin;
	private final ProxyServer proxy;
    private final Logger logger;
    private final Map<Long, LoggingChannelHandler> handlers;

	private boolean useChatEvent = true;
	private boolean useCommandEvent = true;
	private boolean useConnectEvent = true;
	private boolean useDisconnectEvent = true;

	public LoggingManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();

        this.handlers = new HashMap<>();

        parseConfig(config, false);
    }

    public void init() {
		//Can't schedule tasks until ProxyInitializeEvent
        proxy.getScheduler().buildTask(plugin, () ->
				handlers.forEach((id, handler) -> handler.updateLogsPerMessage()))
				.repeat(5, TimeUnit.SECONDS)
				.delay(5, TimeUnit.SECONDS)
				.schedule();

        handlers.forEach((id, handler) -> handler.init());
	}

    private void parseConfig(ConfigurationNode config, boolean reload) {
        Set<Long> existing = new HashSet<>(handlers.keySet());

		useChatEvent = config.getNode("events", "use-chat-event").getBoolean(true);
		useCommandEvent = config.getNode("events", "use-command-event").getBoolean(true);
		useConnectEvent = config.getNode("events", "use-connect-event").getBoolean(true);
		useDisconnectEvent = config.getNode("events", "use-disconnect-event").getBoolean(true);

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
            	LoggingChannelHandler handler = new LoggingChannelHandler(plugin, channelId, channelConfig);
                handlers.put(channelId, handler);

                if(reload) {
                	handler.init();
				}
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
		if(!useConnectEvent) {
			return;
		}

		Player player = event.getPlayer();
		LogEntry joinLog = LogEntry.builder().type(LogType.JOIN).player(player).build();

		logEvent(joinLog);

		if(event.getPreviousServer() != null) {
			joinLog = LogEntry.builder().type(LogType.LEAVE).player(player).server(event.getPreviousServer())
					.build();

			logEvent(joinLog);
		}
	}

	@Subscribe(order = PostOrder.LAST)
	public void onDisconnect(DisconnectEvent event) {
		if(!useDisconnectEvent) {
			return;
		}

		Player player = event.getPlayer();
		LogEntry leaveLog = LogEntry.builder().type(LogType.LEAVE).player(player).build();

		logEvent(leaveLog);
	}

    @Subscribe(order = PostOrder.LAST)
    public void onPlayerChat(PlayerChatEvent event) {
        if(!useChatEvent || !event.getResult().isAllowed()) {
            return;
        }

        LogEntry chatLog = LogEntry.builder().type(LogType.CHAT).player(event.getPlayer()).replacements(
				Map.of("message", event.getMessage())).build();

        logEvent(chatLog);
    }

    @Subscribe(order = PostOrder.LATE)
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
        parseConfig(config, true);
    }
}
