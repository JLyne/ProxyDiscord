package me.prouser123.bungee.discord.listeners;

import me.prouser123.bungee.discord.*;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;

import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.HashMap;
import java.util.UUID;

public class JoinLeave implements Listener {
	private static VerificationManager verificationManager = null;
	private static KickManager kickManager = null;
	private static LoggingManager loggingManager = null;

	private HashMap<UUID, Boolean> firstJoin;

	public JoinLeave() {
		verificationManager = Main.inst().getVerificationManager();
		kickManager = Main.inst().getKickManager();
		loggingManager = Main.inst().getLoggingManager();

		firstJoin = new HashMap<>();
	}

	@EventHandler
	public void onPostLogin(PostLoginEvent event) {
		firstJoin.put(event.getPlayer().getUniqueId(), true);
	}

	@EventHandler
	public void onServerConnected(ServerConnectedEvent event) {
		ProxiedPlayer player = event.getPlayer();

		if(!firstJoin.get(player.getUniqueId())) {
			return;
		}

		firstJoin.put(player.getUniqueId(), false);
		loggingManager.logJoin(player);

		String text;
		VerificationResult result = verificationManager.checkVerificationStatus(player);

		player.sendMessage(new ComponentBuilder(ChatMessages.getMessage("join-welcome"))
				.color(ChatColor.GREEN).create());

		switch(result) {
			case NOT_LINKED:
				Main.inst().getLogger().info("Unlinked player " + player.getName() + " joined");

				text = ChatMessages.getMessage("join-not-linked");
				player.sendMessage(new ComponentBuilder(text).color(ChatColor.YELLOW).create());

				kickManager.addPlayer(player);
				return;

			case LINKED_NOT_VERIFIED:
				Main.inst().getLogger().info("Linked and unverified player " + player.getName() + " joined");

				text = ChatMessages.getMessage("join-linked-not-verified");
				player.sendMessage(new ComponentBuilder(text).color(ChatColor.YELLOW).create());

				kickManager.addPlayer(player);

			case VERIFIED:
				Main.inst().getLogger().info("Verified player " + player.getName() + " joined");
				Main.inst().getAnnouncementManager().sendLatestAnnouncement(player);

				return;
		}

		if(!Main.inst().getDiscord().isConnected()) {
			player.sendMessage(new ComponentBuilder(ChatMessages.getMessage("discord-issues")).color(ChatColor.RED).create());
		}
	}
	
	@EventHandler
	public void onDisconnect(PlayerDisconnectEvent event) {
		ProxiedPlayer player = event.getPlayer();

		if(!firstJoin.get(player.getUniqueId())) {
			loggingManager.logLeave(player);
		}

		firstJoin.remove(player.getUniqueId());
		kickManager.removePlayer(player);
	}
}