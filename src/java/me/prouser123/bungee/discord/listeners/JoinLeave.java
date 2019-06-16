package me.prouser123.bungee.discord.listeners;

import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.VerificationManager;
import me.prouser123.bungee.discord.VerificationResult;
import me.prouser123.bungee.discord.ChatMessages;
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

	public JoinLeave(TextChannel logChannel) {
		JoinLeave.logChannel = logChannel;
		verificationManager = Main.inst().getVerificationManager();
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
					return;

				case LINKED_NOT_VERIFIED:
					text = ChatMessages.getMessage("join-linked-not-verified");

					player.sendMessage(new ComponentBuilder(text).color(ChatColor.YELLOW).create());
			}
		}
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerDisconnectEvent event) {
		logChannel.sendMessage("`" + event.getPlayer().getName() + "` has left the network.");
	}
}