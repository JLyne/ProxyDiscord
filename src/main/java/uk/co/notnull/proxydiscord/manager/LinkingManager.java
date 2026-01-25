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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.entities.User;
import org.spongepowered.configurate.ConfigurationNode;

import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.api.LinkResult;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.bot.commands.Link;
import uk.co.notnull.proxydiscord.api.events.PlayerLinkEvent;
import uk.co.notnull.proxydiscord.api.events.PlayerUnlinkEvent;
import uk.co.notnull.proxydiscord.bot.commands.Unlink;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public final class LinkingManager implements uk.co.notnull.proxydiscord.api.manager.LinkingManager {
    private final ProxyDiscord plugin;
    private final ProxyServer proxy;

    private BiMap<UUID, Long> links;
    private BiMap<String, UUID> pendingLinks;
    private String linkingSecret;

    private final Logger logger;
    private Link linkCommand;
    private Unlink unlinkCommand;

    public LinkingManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();

        this.loadLinks();

        parseConfig(config);
    }

    public void init() {
        //Can't schedule tasks until ProxyInitializeEvent
        proxy.getScheduler().buildTask(plugin, () -> {
            plugin.getDebugLogger().info("Saving linked accounts");
            saveLinks();
        }).repeat(300, TimeUnit.SECONDS).delay(300, TimeUnit.SECONDS).schedule();
    }

    private void parseConfig(ConfigurationNode config) {
		//TODO: Validate
		this.linkingSecret = config.node("linking", "secret").getString();
    }

    public boolean isLinked(Player player) {
        return this.links.containsKey(player.getUniqueId());
    }

    public boolean isLinked(UUID uuid) {
        return this.links.containsKey(uuid);
    }

    public boolean isLinked(long discordId) {
        return this.links.inverse().containsKey(discordId);
    }

    public UUID getLinked(Long discordId) {
        return this.links.inverse().get(discordId);
    }

    public UUID getLinked(User user) {
        return this.links.inverse().get(user.getIdLong());
    }

    public Long getLinked(Player player) {
        return this.links.get(player.getUniqueId());
    }

    public Long getLinked(UUID uuid) {
        return this.links.get(uuid);
    }

    public String getLinkingToken(Player player) {
        return getLinkingToken(player.getUniqueId());
    }

    public String getLinkingToken(UUID uuid) {
        if(this.pendingLinks.containsValue(uuid)) {
            return this.pendingLinks.inverse().get(uuid);
        }

        if(this.links.containsKey(uuid)) {
            return "";
        }

        String token;

        String characters = "0123456789ABCDEFGHIJKLMNPQRUVWXYZ";
        SecureRandom rnd = new SecureRandom();

        do {
           StringBuilder sb = new StringBuilder(8);

           for(int i = 0; i < 8; i++) {
               sb.append(characters.charAt(rnd.nextInt(characters.length())));
           }

           token = sb.toString();
        } while(this.pendingLinks.containsKey(token));

        this.pendingLinks.put(token, uuid);

        return token;
    }

    public LinkResult completeLink(String token, Long discordId) {
        //Account already linked
        if(this.links.containsValue(discordId)) {
            return LinkResult.ALREADY_LINKED;
        }

        if(token.isEmpty()) {
            return LinkResult.NO_TOKEN;
        }

        //Token doesn't exist
        if(!this.pendingLinks.containsKey(token)) {
            return LinkResult.INVALID_TOKEN;
        }

        UUID uuid = this.pendingLinks.get(token);

        if(uuid == null) {
            return LinkResult.INVALID_TOKEN;
        }

        this.links.put(uuid, discordId);
        this.pendingLinks.remove(token);

        fireLinkEvent(uuid, discordId);

        return LinkResult.SUCCESS;
    }

    public LinkResult manualLink(UUID uuid, Long discordId) {
        //Minecraft account already linked
        if(this.links.containsValue(discordId)) {
            return LinkResult.ALREADY_LINKED;
        }

        //Discord account already linked
        if(this.links.containsKey(uuid)) {
            return LinkResult.ALREADY_LINKED;
        }

        this.links.put(uuid, discordId);

        fireLinkEvent(uuid, discordId);

        return LinkResult.SUCCESS;
    }

    public Long unlink(Player player) {
        Long previousLink = this.links.remove(player.getUniqueId());

        if(previousLink != null) {
            fireUnlinkEvent(player.getUniqueId(), previousLink);
        }

        return previousLink;
    }

    public Long unlink(UUID uuid) {
        Long previousLink = this.links.remove(uuid);

        if(previousLink != null) {
            fireUnlinkEvent(uuid, previousLink);
        }

        return previousLink;
    }

    public UUID unlink(long discordId) {
        UUID uuid = this.links.inverse().remove(discordId);

        if(uuid != null) {
            fireUnlinkEvent(uuid, discordId);
        }

        return uuid;
    }

    public void saveLinks() {
        try {
            File folder = plugin.getDataDirectory().toFile();
            File saveFile = new File(folder, "links.sav");

            if (!saveFile.exists() && !saveFile.createNewFile()) {
                throw new IOException("Could not create " + saveFile);
            }

            FileOutputStream saveStream = new FileOutputStream(saveFile);
            ObjectOutputStream save = new ObjectOutputStream(saveStream);

            save.writeObject(this.links);
            save.writeObject(this.pendingLinks);
        } catch (IOException e) {
            logger.warn("Could not save linked accounts to disk");
        }
    }

    private void fireLinkEvent(UUID uuid, Long linked) {
        Optional<Player> player = proxy.getPlayer(uuid);

        if(player.isPresent()) {
            proxy.getEventManager().fireAndForget(new PlayerLinkEvent(player.get(), linked));
        } else {
            proxy.getEventManager().fireAndForget(new PlayerLinkEvent(uuid, linked));
        }
    }

    private void fireUnlinkEvent(UUID uuid, Long previousLink) {
        Optional<Player> player = proxy.getPlayer(uuid);

        if(player.isPresent()) {
            proxy.getEventManager().fireAndForget(new PlayerUnlinkEvent(player.get(), previousLink));
        } else {
            proxy.getEventManager().fireAndForget(new PlayerUnlinkEvent(uuid, previousLink));
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLinks() {
        try {
            File folder = plugin.getDataDirectory().toFile();

            File saveFile = new File(folder, "links.sav");
            File oldSaveFile = new File(folder, "links_old.sav");

            if (saveFile.exists()) {
                FileInputStream saveStream = new FileInputStream(saveFile);
                ObjectInputStream save = new ObjectInputStream(saveStream);

                BiMap<UUID, Long> links = (BiMap<UUID, Long>) save.readObject();
                BiMap<String, UUID> pendingLinks = (BiMap<String, UUID>) save.readObject();

                this.links = Maps.synchronizedBiMap(links);
                this.pendingLinks = Maps.synchronizedBiMap(pendingLinks);
            } else if(oldSaveFile.exists()) {
                this.links = Maps.synchronizedBiMap(HashBiMap.create(1024));
                this.pendingLinks = Maps.synchronizedBiMap(HashBiMap.create(1024));

                logger.info("Importing old links file...");
                FileInputStream saveStream = new FileInputStream(oldSaveFile);
                ObjectInputStream save = new ObjectInputStream(saveStream);

                HashBiMap<String, Long> links = (HashBiMap<String, Long>) save.readObject();
                HashBiMap<String, String> pendingLinks = (HashBiMap<String, String>) save.readObject();

                links.forEach((String key, Long value) -> {
                    try {
                        this.links.put(UUID.fromString(key), value);
                    } catch(IllegalArgumentException e) {
						logger.warn("Invalid UUID for discord ID {}. Skipping.", value);
                    }
                });

                pendingLinks.forEach((String key, String value) -> {
                    try {
                        this.pendingLinks.put(key, UUID.fromString(value));
                    } catch(IllegalArgumentException e) {
						logger.warn("Invalid UUID for linking code {}. Skipping.", value);
                    }
                });

                logger.info("Saving new links file...");
                saveLinks();
				logger.info("Imported {} linked accounts and {} pending links.", this.links.size(),
							this.pendingLinks.size());
            } else {
                this.links = Maps.synchronizedBiMap(HashBiMap.create(1024));
                this.pendingLinks = Maps.synchronizedBiMap(HashBiMap.create(1024));
            }
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            this.links = Maps.synchronizedBiMap(HashBiMap.create(1024));
            this.pendingLinks = Maps.synchronizedBiMap(HashBiMap.create(1024));
            logger.warn("Could not load linked accounts from disk");
        }
    }

    public String getLinkingSecret() {
        return linkingSecret;
    }

    public void reload(ConfigurationNode config) {
        parseConfig(config);
    }
}
