package me.prouser123.bungee.discord;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import me.prouser123.bungee.discord.exceptions.AlreadyLinkedException;
import me.prouser123.bungee.discord.exceptions.InvalidTokenException;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LinkingManager {
    private HashBiMap<String, Long> links;
    private HashBiMap<String, String> pendingVerifications;

    public LinkingManager() {
        this.links = HashBiMap.create(1024);
        this.pendingVerifications = HashBiMap.create(1024);
    }

    public boolean isLinked(String uuid) {
        return this.links.containsKey(uuid);
    }

    public boolean isLinked(UUID uuid) {
        return this.links.containsKey(uuid.toString());
    }

    public boolean isLinked(ProxiedPlayer player) {
        return this.links.containsKey(player.getUniqueId().toString());
    }

    public Long getLinked(String uuid) {
        return this.links.get(uuid);
    }

    public Long getLinked(UUID uuid) {
        return this.links.get(uuid.toString());
    }

    public Long getLinked(ProxiedPlayer player) {
        return this.links.get(player.getUniqueId().toString());
    }

    public String startLink(String uuid) throws AlreadyLinkedException {
        String token;

        if(this.links.containsKey(uuid)) {
            throw new AlreadyLinkedException("Discord account is already linked to a Minecraft player");
        }

        if (this.pendingVerifications.containsValue(uuid)) {
           return this.pendingVerifications.inverse().get(uuid);
        }

        do {
            token = Long.toHexString(Double.doubleToLongBits(Math.random()));
        } while(this.pendingVerifications.containsKey(token));

        this.pendingVerifications.put(token, uuid);

        return token;
    }

    public String startLink(UUID uuid) throws AlreadyLinkedException {
        return startLink(uuid.toString());
    }

    public String startLink(ProxiedPlayer player) throws AlreadyLinkedException {
        return startLink(player.getUniqueId().toString());
    }

    public void completeLink(String token, Long discordId) throws AlreadyLinkedException, InvalidTokenException {
        if(this.links.containsValue(discordId)) {
            throw new AlreadyLinkedException("Discord account is already linked to a Minecraft player");
        }

        if(!this.pendingVerifications.containsKey(token)) {
            throw new InvalidTokenException("Token not found");
        }

        String player = this.pendingVerifications.get(token);

        this.links.put(player, discordId);
    }

    public void unlink(String uuid) {
        this.links.remove(uuid);
    }
}
