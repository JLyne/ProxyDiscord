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

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import org.spongepowered.configurate.ConfigurationNode;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.announcements.AnnouncementChannelHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnnouncementManager {
    private final ProxyDiscord plugin;
    private ConfigurationNode config;
    private final ConcurrentHashMap<Guild, Map<StandardGuildMessageChannel, AnnouncementChannelHandler>> handlers;
    private final Logger logger;

    public AnnouncementManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.config = config.node("announcement-channels");
        this.logger = plugin.getLogger();

        handlers = new ConcurrentHashMap<>();
    }

    public void findChannels(Guild guild) {
        removeChannels(guild);
        Map<StandardGuildMessageChannel, AnnouncementChannelHandler> newHandlers = new ConcurrentHashMap<>();

        config.childrenMap().forEach((Object id, ConfigurationNode settings) -> {
            String channelID = id.toString();
            boolean serverList = !settings.node("servers").virtual();
            List<? extends ConfigurationNode> servers = settings.node("servers").childrenList();

            if(serverList && servers.isEmpty()) {
				logger.warn("Skipping announcement channel {}. Server list defined but empty.", channelID);
                return;
            }

            try {
                plugin.getLogger().info(channelID);
                StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, channelID);
                AnnouncementChannelHandler handler;

                if (channel == null) {
                    return;
                }

                if(serverList) {
                    List<String> list = new ArrayList<>();
                    servers.forEach(s -> list.add(s.getString()));

                    handler = new AnnouncementChannelHandler(channel, list);
                } else {
                    handler = new AnnouncementChannelHandler(channel);
                }

                newHandlers.put(channel, handler);
                guild.getJDA().addEventListener(handler);
                plugin.getProxy().getEventManager().register(plugin, handler);
            } catch(RuntimeException e) {
				logger.warn("Unable to add handler for announcement channel {}", channelID, e);
            }
        });

        handlers.put(guild, newHandlers);
    }

    public void removeChannels(Guild guild) {
        handlers.computeIfPresent(guild, (_, value) -> {
            for (AnnouncementChannelHandler handler : value.values()) {
                guild.getJDA().removeEventListener(handler);
                plugin.getProxy().getEventManager().unregisterListener(plugin, handler);
            }

            return null;
        });
    }

    public void reload(ConfigurationNode config) {
        this.config = config.node("announcement-channels");

        for (Guild guild : plugin.getDiscord().getJDA().getGuilds()) {
            findChannels(guild);
        }
    }
}
