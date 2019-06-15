package me.prouser123.bungee.discord;

import com.google.common.collect.HashBiMap;
import me.prouser123.bungee.discord.exceptions.AlreadyLinkedException;
import me.prouser123.bungee.discord.exceptions.InvalidTokenException;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.javacord.api.entity.user.User;

import java.security.SecureRandom;
import java.util.Base64;
public class LinkingManager {
    private HashBiMap<String, Long> links;
    private HashBiMap<String, String> pendingLinks;

    public LinkingManager() {
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

    public String getLinked(User user) {
        return this.links.inverse().get(user.getId());
    }

    public Long getLinked(ProxiedPlayer player) {
        return this.links.get(player.getUniqueId().toString());
    }

    public String startLink(String uuid) throws AlreadyLinkedException {
        if(this.links.containsKey(uuid)) {
            throw new AlreadyLinkedException("Discord account is already linked to a Minecraft player");
        }

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

    public String startLink(ProxiedPlayer player) throws AlreadyLinkedException {
        return startLink(player.getUniqueId().toString());
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
