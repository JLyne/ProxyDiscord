package me.prouser123.bungee.discord;

import com.google.common.collect.HashBiMap;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import com.velocitypowered.api.proxy.Player;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LinkingManager {
    private HashBiMap<String, Long> links;
    private HashBiMap<String, String> pendingLinks;
    private final String linkingUrl;
    private final String linkingChannelId;

    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public LinkingManager(String linkingUrl, String linkingChannelId) {
        this.proxy = Main.inst().getProxy();
        this.logger = Main.inst().getLogger();

        this.linkingUrl = linkingUrl;
        this.linkingChannelId = linkingChannelId;
        this.loadLinks();

        proxy.getScheduler().buildTask(Main.inst(), () -> {
            Main.inst().getDebugLogger().info("Saving linked accounts");
            saveLinks();
        }).repeat(300, TimeUnit.SECONDS).delay(300, TimeUnit.SECONDS).schedule();

        if(linkingChannelId != null) {
            findChannel();
        }

        Main.inst().getDiscord().getApi().addReconnectListener(event -> {
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

    String getLinked(Long discordId) {
        return this.links.inverse().get(discordId);
    }

    String getLinked(User user) {
        return this.links.inverse().get(user.getId());
    }

    Long getLinked(Player player) {
        return this.links.get(player.getUniqueId().toString());
    }

    Long getLinked(UUID uuid) {
        return this.links.get(uuid.toString());
    }

    public void startLink(Player player) {
        String uuid = player.getUniqueId().toString();

        if(this.links.containsKey(uuid)) {
            TextComponent message = TextComponent.of(ChatMessages.getMessage("link-already-linked"));
            message.color(TextColor.RED);

            player.sendMessage(message);

            return;
        }

        String token = getLinkingToken(uuid);
        String url = linkingUrl.replace("[token]", token);

        TextComponent message = TextComponent.of(ChatMessages.getMessage("link"));
        message.color(TextColor.LIGHT_PURPLE);
        message.clickEvent(ClickEvent.openUrl(url));
        message.hoverEvent(HoverEvent.showText(TextComponent.of("Discord account linking instructions")));

        player.sendMessage(message);
    }

    private String getLinkingToken(String uuid) {
        if (this.pendingLinks.containsValue(uuid)) {
            return this.pendingLinks.inverse().get(uuid);
        }

        String token;

        do {
            SecureRandom rnd = new SecureRandom();
            byte[] bytes = new byte[8];
            rnd.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while(this.pendingLinks.containsKey(token));

        this.pendingLinks.put(token, uuid);

        return token;
    }

    public LinkResult completeLink(String token, Long discordId) {
        //Account already linked
        if(this.links.containsValue(discordId)) {
            //Said account doesn't have verified role
            if(!Main.inst().getVerificationManager().hasVerifiedRole(discordId)) {
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

        VerificationResult result =  Main.inst().getVerificationManager().checkVerificationStatus(discordId);

        Optional<Player> onlinePlayer = proxy.getPlayer(UUID.fromString(player));

        if(onlinePlayer.isPresent()) {
            if(result == VerificationResult.VERIFIED) {
                onlinePlayer.get().sendMessage(TextComponent.of(ChatMessages.getMessage("link-success"))
                        .color(TextColor.GREEN));

                return LinkResult.SUCCESS;
            } else {
                onlinePlayer.get().sendMessage(TextComponent.of(ChatMessages.getMessage("link-not-verified"))
                        .color(TextColor.YELLOW));

                return LinkResult.NOT_VERIFIED;
            }
        }

        return result == VerificationResult.VERIFIED ? LinkResult.SUCCESS : LinkResult.NOT_VERIFIED;
    }

    public void unlink(Player player) {
        this.links.remove(player.getUniqueId().toString());
    }

    public void saveLinks() {
        try {
            File folder = new File(proxy.getPluginsFolder(), "BungeeDiscord");
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
            File folder = new File(proxy.getPluginsFolder(), "BungeeDiscord");
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
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            this.links = HashBiMap.create(1024);
            this.pendingLinks = HashBiMap.create(1024);
            logger.warn("Could not load linked accounts from disk");
        }
    }

    private void findChannel() {
        Optional <TextChannel> linkingChannel = Main.inst().getDiscord().getApi().getTextChannelById(linkingChannelId);

        if(!linkingChannel.isPresent()) {
            logger.warn("Unable to find linking channel. Did you put a valid channel ID in the config?");
        }
    }
}
