package uk.co.notnull.proxydiscord;

import com.google.common.collect.HashBiMap;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LinkingManager {
    private HashBiMap<String, Long> links;
    private HashBiMap<String, String> pendingLinks;
    private final String linkingSecret;
    private final String linkingChannelId;

    private final ProxyServer proxy;
    private final Logger logger;

    public LinkingManager(String linkingChannelId, String linkingSecret) {
        this.proxy = ProxyDiscord.inst().getProxy();
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
        return this.links.containsKey(player.getUniqueId().toString());
    }

    public boolean isLinked(UUID uuid) {
        return this.links.containsKey(uuid.toString());
    }

    public boolean isLinked(long discordId) {
        return this.links.inverse().containsKey(discordId);
    }

    public UUID getLinked(Long discordId) {
        String linked = this.links.inverse().get(discordId);

        return linked != null ? UUID.fromString(linked) : null;
    }

    public String getLinked(User user) {
        return this.links.inverse().get(user.getId());
    }

    public Long getLinked(Player player) {
        return this.links.get(player.getUniqueId().toString());
    }

    public Long getLinked(UUID uuid) {
        return this.links.get(uuid.toString());
    }

    public String getLinkingToken(Player player) {
        return getLinkingToken(player.getUniqueId().toString());
    }

    public String getLinkingToken(String uuid) {
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

        String player = this.pendingLinks.get(token);

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
        if(this.links.containsKey(uuid.toString())) {
            //Said account doesn't have verified role
            if(!ProxyDiscord.inst().getVerificationManager().hasVerifiedRole(this.links.get(uuid.toString()))) {
                return LinkResult.ALREADY_LINKED_NOT_VERIFIED;
            }

            return LinkResult.ALREADY_LINKED;
        }

        this.links.put(uuid.toString(), discordId);

        VerificationResult result = ProxyDiscord.inst().getVerificationManager().checkVerificationStatus(discordId);

        return result == VerificationResult.VERIFIED ? LinkResult.SUCCESS : LinkResult.NOT_VERIFIED;
    }

    public void unlink(Player player) {
        this.links.remove(player.getUniqueId().toString());
    }

    public void unlink(UUID uuid) {
        this.links.remove(uuid.toString());
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

    private void loadLinks() {
        try {
            File folder = ProxyDiscord.inst().getDataDirectory().toFile();
            File saveFile = new File(folder, "links.sav");

            if (!saveFile.exists()) {
                this.links = HashBiMap.create(1024);
                this.pendingLinks = HashBiMap.create(1024);

                return;
            }

            FileInputStream saveStream = new FileInputStream(saveFile);
            ObjectInputStream save = new ObjectInputStream(saveStream);

            this.links = (HashBiMap<String, Long>) save.readObject();
            this.pendingLinks = (HashBiMap<String, String>) save.readObject();

            this.pendingLinks.replaceAll((value, key) -> value.toUpperCase());
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            this.links = HashBiMap.create(1024);
            this.pendingLinks = HashBiMap.create(1024);
            logger.warn("Could not load linked accounts from disk");
        }
    }

    private void findChannel() {
        Optional <TextChannel> linkingChannel = ProxyDiscord.inst().getDiscord().getApi().getTextChannelById(linkingChannelId);

        if(linkingChannel.isEmpty()) {
            logger.warn("Unable to find linking channel. Did you put a valid channel ID in the config?");
        }
    }

    public String getLinkingSecret() {
        return linkingSecret;
    }
}
