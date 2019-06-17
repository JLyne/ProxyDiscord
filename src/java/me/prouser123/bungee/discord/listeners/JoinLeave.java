package me.prouser123.bungee.discord.listeners;

import me.prouser123.bungee.discord.*;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.javacord.api.entity.channel.TextChannel;

import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class JoinLeave implements Listener {
	public static TextChannel logChannel = null;
	private static VerificationManager verificationManager = null;
	private static KickManager kickManager = null;

	public JoinLeave(TextChannel logChannel) {
		JoinLeave.logChannel = logChannel;
		verificationManager = Main.inst().getVerificationManager();
		kickManager = Main.inst().getKickManager();
	}
	
	@EventHandler
	public void onPlayerJoin(PostLoginEvent event) {
		ProxiedPlayer player = event.getPlayer();

		if(logChannel != null) {
			logChannel.sendMessage("`" + player.getName() + "` has joined the network.");
		}

		if(verificationManager != null) {
			String text;
			VerificationResult result = verificationManager.checkVerificationStatus(player);

			switch(result) {
				case NOT_LINKED:
					text = ChatMessages.getMessage("join-welcome");
					player.sendMessage(new ComponentBuilder(text).color(ChatColor.GREEN).create());

					text = ChatMessages.getMessage("join-not-linked");
					player.sendMessage(new ComponentBuilder(text).color(ChatColor.YELLOW).create());

					kickManager.addPlayer(player);
					return;

				case LINKED_NOT_VERIFIED:
					text = ChatMessages.getMessage("join-linked-not-verified");
					player.sendMessage(new ComponentBuilder(text).color(ChatColor.YELLOW).create());

					kickManager.addPlayer(player);
			}
		}
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerDisconnectEvent event) {
		kickManager.removePlayer(event.getPlayer());
		logChannel.sendMessage("`" + event.getPlayer().getName() + "` has left the network.");
	}
}