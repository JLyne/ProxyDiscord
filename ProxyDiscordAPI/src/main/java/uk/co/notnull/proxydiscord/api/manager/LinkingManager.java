package uk.co.notnull.proxydiscord.api.manager;

import com.velocitypowered.api.proxy.Player;
import uk.co.notnull.proxydiscord.api.LinkResult;

import java.util.UUID;

@SuppressWarnings("unused")
public interface LinkingManager {
	boolean isLinked(Player player);

    boolean isLinked(UUID uuid);

    boolean isLinked(long discordId);

    UUID getLinked(Long discordId);

    Long getLinked(Player player);

    Long getLinked(UUID uuid);

    String getLinkingToken(Player player);

    String getLinkingToken(UUID uuid);

    LinkResult completeLink(String token, Long discordId);

    LinkResult manualLink(UUID uuid, Long discordId);

    void unlink(Player player);

    void unlink(UUID uuid);

    void unlink(long discordId);

    String getLinkingSecret();
}
