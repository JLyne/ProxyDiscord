package me.prouser123.bungee.discord;

import com.google.common.collect.HashBiMap;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.user.User;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LinkingManager {
    private HashBiMap<String, Long> links;
    private HashBiMap<String, String> pendingLinks;
    private final String linkingUrl;
    private final String linkingChannelId;

    LinkingManager(String linkingUrl, String linkingChannelId) {
        this.linkingUrl = linkingUrl;
        this.linkingChannelId = linkingChannelId;
        this.loadLinks();

        Main.inst().getProxy().getScheduler().schedule(Main.inst(), () -> {
            Main.inst().getDebugLogger().info("Saving linked accounts");
            saveLinks();
        }, 300, 300, TimeUnit.SECONDS);

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

    public boolean isLinked(ProxiedPlayer player) {
        return this.links.containsKey(player.getUniqueId().toString());
    }

    String getLinked(Long discordId) {
        return this.links.inverse().get(discordId);
    }

    String getLinked(User user) {
        return this.links.inverse().get(user.getId());
    }

    public Long getLinked(ProxiedPlayer player) {
        return this.links.get(player.getUniqueId().toString());
    }

    public void startLink(ProxiedPlayer player) {
        String uuid = player.getUniqueId().toString();

        if(this.links.containsKey(uuid)) {
            TextComponent message = new TextComponent(ChatMessages.getMessage("link-already-linked"));
            message.setColor(ChatColor.RED);

            player.sendMessage(message);

            return;
        }

        String token = getLinkingToken(uuid);
        String url = linkingUrl.replace("[token]", token);

        TextComponent message = new TextComponent(ChatMessages.getMessage("link"));
        message.setColor(ChatColor.LIGHT_PURPLE);
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Discord account linking instructions").create()));

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

        ProxiedPlayer onlinePlayer = ProxyServer.getInstance().getPlayer(UUID.fromString(player));

		if(onlinePlayer != null) {
			if(result == VerificationResult.VERIFIED) {
				onlinePlayer.sendMessage(new ComponentBuilder(ChatMessages.getMessage("link-success"))
						.color(ChatColor.GREEN).create());

				return LinkResult.NOT_VERIFIED;
			} else {
				onlinePlayer.sendMessage(new ComponentBuilder(ChatMessages.getMessage("link-not-verified"))
						.color(ChatColor.YELLOW).create());

				return LinkResult.SUCCESS;
			}
		}

        return result == VerificationResult.VERIFIED ? LinkResult.SUCCESS : LinkResult.NOT_VERIFIED;
    }

    public void unlink(ProxiedPlayer player) {
        this.links.remove(player.getUniqueId().toString());
    }

    public void saveLinks() {
        try {
            File folder = new File(Main.inst().getProxy().getPluginsFolder(), "BungeeDiscord");
            File saveFile = new File(folder, "links.sav");

            if (!saveFile.exists() && !saveFile.createNewFile()) {
                throw new IOException("Could not create " + saveFile);
            }

            FileOutputStream saveStream = new FileOutputStream(saveFile);
            ObjectOutputStream save = new ObjectOutputStream(saveStream);

            save.writeObject(this.links);
            save.writeObject(this.pendingLinks);
        } catch (IOException e) {
            Main.inst().getLogger().warning("Could not save linked accounts to disk");
        }
    }

    private void loadLinks() {
        try {
            File folder = new File(Main.inst().getProxy().getPluginsFolder(), "BungeeDiscord");
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
            Main.inst().getLogger().warning("Could not load linked accounts from disk");
        }
    }

    private void findChannel() {
        Optional <TextChannel> linkingChannel = Main.inst().getDiscord().getApi().getTextChannelById(linkingChannelId);

        if(!linkingChannel.isPresent()) {
            Main.inst().getLogger().info("Unable to find linking channel. Did you put a valid channel ID in the config?");
        }
    }
}
