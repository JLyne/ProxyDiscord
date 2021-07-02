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

import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.announcements.AnnouncementChannelHandler;

import java.util.ArrayList;
import java.util.List;

public class AnnouncementManager {
    private ConfigurationNode config;
    private final List<AnnouncementChannelHandler> handlers;
    private final Logger logger;

    public AnnouncementManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.config = config.getNode("announcement-channels");
        this.logger = plugin.getLogger();

        handlers = new ArrayList<>();
        findChannels();

        plugin.getDiscord().getApi().addReconnectListener(event -> findChannels());
    }

    private void findChannels() {
        handlers.forEach(AnnouncementChannelHandler::remove);
        handlers.clear();

        config.getChildrenMap().forEach((Object id, ConfigurationNode settings) -> {
            String channelID = id.toString();
            boolean serverList = !settings.getNode("servers").isVirtual();
            List<? extends ConfigurationNode> servers = settings.getNode("servers").getChildrenList();

            if(serverList && servers.isEmpty()) {
                logger.warn("Skipping announcement channel " + channelID + ". Server list defined but empty.");
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

    public void reload(ConfigurationNode config) {
        this.config = config.getNode("announcement-channels");
        findChannels();
    }
}
