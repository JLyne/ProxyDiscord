package uk.co.notnull.proxydiscord.manager;

import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.announcements.AnnouncementChannelHandler;

import java.util.ArrayList;
import java.util.List;

public class AnnouncementManager {
    private final ConfigurationNode config;
    private final List<AnnouncementChannelHandler> handlers;
    private final Logger logger;

    public AnnouncementManager(ConfigurationNode config) {
        this.config = config;
        this.logger = ProxyDiscord.inst().getLogger();

        handlers = new ArrayList<>();
        findChannels();

        ProxyDiscord.inst().getDiscord().getApi().addReconnectListener(event -> findChannels());
    }

    private void findChannels() {
        handlers.forEach(AnnouncementChannelHandler::remove);
        handlers.clear();

        config.getChildrenMap().forEach((Object id, ConfigurationNode settings) -> {
            String channelID = id.toString();
            boolean serverList = settings.getNode("serverList").getBoolean(false);
            List<? extends ConfigurationNode> servers = settings.getNode("servers").getChildrenList();

            if(serverList && servers.isEmpty()) {
                logger.warn("Skipping announcement channel " + channelID + ". serverList enabled but no servers specified.");
                return;
            }

            try {
                if(serverList) {
                    List<String> list = new ArrayList<>();
                    servers.forEach(s -> list.add(s.getString()));

                    handlers.add(new AnnouncementChannelHandler(channelID, list));
                } else {
                    handlers.add(new AnnouncementChannelHandler(channelID));
                }
            } catch(RuntimeException e) {
                logger.warn("Unable to add handler for announcement channel " + channelID);
                e.printStackTrace();
            }
        });
    }
}
