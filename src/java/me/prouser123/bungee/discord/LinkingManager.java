package me.prouser123.bungee.discord;

import com.google.common.collect.HashBiMap;
import me.prouser123.bungee.discord.exceptions.AlreadyLinkedException;
import me.prouser123.bungee.discord.exceptions.InvalidTokenException;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.javacord.api.entity.user.User;

import java.security.SecureRandom;
import java.util.Base64;
public class LinkingManager {
    private HashBiMap<String, Long> links;
    private HashBiMap<String, String> pendingLinks;
    private String linkingUrl;

    public LinkingManager(String linkingUrl) {
        this.linkingUrl = linkingUrl;
        this.links = HashBiMap.create(1024);
        this.pendingLinks = HashBiMap.create(1024);
    }

    public boolean isLinked(String uuid) {
        return this.links.containsKey(uuid);
    }

    public boolean isLinked(ProxiedPlayer player) {
        return this.links.containsKey(player.getUniqueId().toString());
    }

    public boolean isLinked(Long discordId) {
        return this.links.containsValue(discordId);
    }

    public boolean isLinked(User user) {
        return this.links.containsValue(user.getId());
    }

    public Long getLinked(String uuid) {
        return this.links.get(uuid);
    }

    public String getLinked(Long discordId) {
        return this.links.inverse().get(discordId);
    }

    String getLinked(User user) {
        return this.links.inverse().get(user.getId());
    }

    public Long getLinked(ProxiedPlayer player) {
        return this.links.get(player.getUniqueId().toString());
    }

    public String startLink(ProxiedPlayer player) {
        String uuid = player.getUniqueId().toString();

        if(this.links.containsKey(uuid)) {
            TextComponent message = new TextComponent(ChatMessages.getMessage("link-already-linked"));
            message.setColor(ChatColor.RED);

            player.sendMessage(message);

            return null;
        }

        String token = getLinkingToken(uuid);
        String url = linkingUrl.replace("[token]", token);

        TextComponent message = new TextComponent(ChatMessages.getMessage("link"));
        message.setColor(ChatColor.LIGHT_PURPLE);
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Discord account linking instructions").create()));

        player.sendMessage(message);

        return token;
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

    public void completeLink(String token, Long discordId) throws AlreadyLinkedException, InvalidTokenException {
        if(this.links.containsValue(discordId)) {
            throw new AlreadyLinkedException("Discord account is already linked to a Minecraft player");
        }

        if(!this.pendingLinks.containsKey(token)) {
            throw new InvalidTokenException("Token not found");
        }

        String player = this.pendingLinks.get(token);

        this.links.put(player, discordId);
    }

    public void unlink(String uuid) {
        this.links.remove(uuid);
    }

    public void unlink(ProxiedPlayer player) {
        this.links.remove(player.getUniqueId().toString());
    }
}
