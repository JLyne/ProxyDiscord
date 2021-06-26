package uk.co.notnull.proxydiscord.api.events;

import com.velocitypowered.api.proxy.Player;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unused")
public final class PlayerLinkEvent {
	private final UUID uuid;
	private final Player player;
	private final long discordId;

	public PlayerLinkEvent(UUID uuid, long discordId) {
		this.uuid = uuid;
		this.player = null;
		this.discordId = discordId;
	}

	public PlayerLinkEvent(Player player, long discordId) {
		this.uuid = player.getUniqueId();
		this.player = player;
		this.discordId = discordId;
	}

	public UUID getUuid() {
		return uuid;
	}

	public Optional<Player> getPlayer() {
		return Optional.ofNullable(player);
	}

	public long getDiscordId() {
		return discordId;
	}
}
