package uk.co.notnull.proxydiscord.manager;

import com.velocitypowered.api.proxy.Player;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.logging.LoggingChannelHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LoggingManager {
    private final ProxyDiscord plugin;
    private final Logger logger;
    private final Map<Long, LoggingChannelHandler> handlers;

    public LoggingManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        this.handlers = new HashMap<>();

        plugin.getProxy().getEventManager().register(plugin, this);

        parseConfig(config);
    }

    public void parseConfig(ConfigurationNode config) {
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

    public void reload(ConfigurationNode config) {
        parseConfig(config);
    }
}
