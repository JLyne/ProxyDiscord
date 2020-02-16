package me.prouser123.bungee.discord.events;

import com.velocitypowered.api.proxy.Player;

public class PlayerUnverifiedEvent {
    private final Player player;

	public PlayerUnverifiedEvent(Player player) {
		this.player = player;
	}

	public Player getPlayer() {
		return player;
	}
}
