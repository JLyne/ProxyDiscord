package uk.co.notnull.proxydiscord;

import com.google.common.collect.HashBiMap;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.bot.commands.Link;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class LinkingManager {
    private HashBiMap<UUID, Long> links;
    private HashBiMap<String, UUID> pendingLinks;
    private final String linkingSecret;
    private final String linkingChannelId;

    private final Logger logger;
    private Link linkCommand;

    public LinkingManager(String linkingChannelId, String linkingSecret) {
        ProxyServer proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();

        this.linkingSecret = linkingSecret;
        this.linkingChannelId = linkingChannelId;
        this.loadLinks();

        proxy.getScheduler().buildTask(ProxyDiscord.inst(), () -> {
            ProxyDiscord.inst().getDebugLogger().info("Saving linked accounts");
            saveLinks();
        }).repeat(300, TimeUnit.SECONDS).delay(300, TimeUnit.SECONDS).schedule();

        if(linkingChannelId != null) {
            findChannel();
        }

        ProxyDiscord.inst().getDiscord().getApi().addReconnectListener(event -> {
            if(linkingChannelId != null) {
                findChannel();
            }
        });
    }

    public boolean isLinkingChannel(TextChannel channel) {
        if(linkingChannelId == null) {
            return true;
        }

        return channel.getIdAsString().equals(linkingChannelId);
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
        return this.links.inverse().get(user.getId());
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

    @SuppressWarnings("SpellCheckingInspection")
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
            //Said account doesn't have verified role
            if(!ProxyDiscord.inst().getVerificationManager().hasVerifiedRole(discordId)) {
                return LinkResult.ALREADY_LINKED_NOT_VERIFIED;
            }

            return LinkResult.ALREADY_LINKED;
        }

        if(token.isEmpty()) {
            return LinkResult.NO_TOKEN;
        }

        //Token doesn't exist
        if(!this.pendingLinks.containsKey(token)) {
            return LinkResult.INVALID_TOKEN;
        }

        UUID player = this.pendingLinks.get(token);

        if(player == null) {
            return LinkResult.INVALID_TOKEN;
        }

        this.links.put(player, discordId);
        this.pendingLinks.remove(token);

        VerificationResult result = ProxyDiscord.inst().getVerificationManager().checkVerificationStatus(discordId);

        return result == VerificationResult.VERIFIED ? LinkResult.SUCCESS : LinkResult.NOT_VERIFIED;
    }

    public LinkResult manualLink(UUID uuid, Long discordId) {
        //Minecraft account already linked
        if(this.links.containsValue(discordId)) {
            //Said account doesn't have verified role
            if(!ProxyDiscord.inst().getVerificationManager().hasVerifiedRole(discordId)) {
                return LinkResult.ALREADY_LINKED_NOT_VERIFIED;
            }

            return LinkResult.ALREADY_LINKED;
        }

        //Discord account already linked
        if(this.links.containsKey(uuid)) {
            //Said account doesn't have verified role
            if(!ProxyDiscord.inst().getVerificationManager().hasVerifiedRole(this.links.get(uuid))) {
                return LinkResult.ALREADY_LINKED_NOT_VERIFIED;
            }

            return LinkResult.ALREADY_LINKED;
        }

        this.links.put(uuid, discordId);

        VerificationResult result = ProxyDiscord.inst().getVerificationManager().checkVerificationStatus(discordId);

        return result == VerificationResult.VERIFIED ? LinkResult.SUCCESS : LinkResult.NOT_VERIFIED;
    }

    public void unlink(Player player) {
        this.links.remove(player.getUniqueId());
    }

    public void unlink(UUID uuid) {
        this.links.remove(uuid);
    }

    public void unlink(long discordId) {
        this.links.inverse().remove(discordId);
    }

    public void saveLinks() {
        try {
            File folder = ProxyDiscord.inst().getDataDirectory().toFile();
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

    @SuppressWarnings("unchecked")
    private void loadLinks() {
        try {
            File folder = ProxyDiscord.inst().getDataDirectory().toFile();

            File saveFile = new File(folder, "links.sav");
            File oldSaveFile = new File(folder, "links_old.sav");

            if (saveFile.exists()) {
                FileInputStream saveStream = new FileInputStream(saveFile);
                ObjectInputStream save = new ObjectInputStream(saveStream);

                this.links = (HashBiMap<UUID, Long>) save.readObject();
                this.pendingLinks = (HashBiMap<String, UUID>) save.readObject();
            } else if(oldSaveFile.exists()) {
                this.links = HashBiMap.create(1024);
                this.pendingLinks = HashBiMap.create(1024);

                logger.info("Importing old links file...");
                FileInputStream saveStream = new FileInputStream(oldSaveFile);
                ObjectInputStream save = new ObjectInputStream(saveStream);

                HashBiMap<String, Long> links = (HashBiMap<String, Long>) save.readObject();
                HashBiMap<String, String> pendingLinks = (HashBiMap<String, String>) save.readObject();

                links.forEach((String key, Long value) -> {
                    try {
                        this.links.put(UUID.fromString(key), value);
                    } catch(IllegalArgumentException e) {
                        logger.warn("Invalid UUID for discord ID " + value + ". Skipping.");
                    }
                });

                pendingLinks.forEach((String key, String value) -> {
                    try {
                        this.pendingLinks.put(key, UUID.fromString(value));
                    } catch(IllegalArgumentException e) {
                        logger.warn("Invalid UUID for linking code " + value + ". Skipping.");
                    }
                });

                logger.info("Saving new links file...");
                saveLinks();
                logger.info("Imported " + this.links.size() + " linked accounts and " + this.pendingLinks.size() + " pending links.");
            } else {
                this.links = HashBiMap.create(1024);
                this.pendingLinks = HashBiMap.create(1024);
            }
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            this.links = HashBiMap.create(1024);
            this.pendingLinks = HashBiMap.create(1024);
            logger.warn("Could not load linked accounts from disk");
        }
    }

    private void findChannel() {
        DiscordApi api = ProxyDiscord.inst().getDiscord().getApi();
        Optional <TextChannel> linkingChannel = api.getTextChannelById(linkingChannelId);

        if(linkingChannel.isEmpty()) {
            logger.warn("Unable to find linking channel. Did you put a valid channel ID in the config?");

            if(linkCommand != null) {
                linkCommand.remove();
                linkCommand = null;
            }
        } else if(linkCommand == null) {
            linkCommand = new Link(this, linkingChannel.get());
        } else {
            linkCommand.setLinkingChannel(linkingChannel.get());
        }
    }

    public String getLinkingSecret() {
        return linkingSecret;
    }
}
