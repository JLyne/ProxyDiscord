package uk.co.notnull.proxydiscord.events;

import com.velocitypowered.api.proxy.Player;

public class PlayerVerifiedEvent {
    private final Player player;

	public PlayerVerifiedEvent(Player player) {
		this.player = player;
	}

	public Player getPlayer() {
		return player;
	}
}
