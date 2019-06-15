package me.prouser123.bungee.discord.listeners;

import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.VerificationManager;
import me.prouser123.bungee.discord.VerificationResult;
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
		verificationManager = Main.inst().getVerifiedRoleManager();
	}
	
	@EventHandler
	public void onPlayerJoin(PostLoginEvent event) {
		ProxiedPlayer player = event.getPlayer();

		if(logChannel != null) {
			logChannel.sendMessage("`" + player.getName() + "` has joined the network.");
		}

		VerificationResult result = verificationManager.checkVerificationStatus(player);

		switch(result) {
			case NOT_LINKED:
				player.sendMessage(new TextComponent("NOT_LINKED"));
				return;

			case LINKED_NOT_VERIFIED:
				player.sendMessage(new TextComponent("LINKED_NOT_VERIFIED"));
				return;

			case VERIFIED:
				player.sendMessage(new TextComponent("VERIFIED"));
		}
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerDisconnectEvent event) {
		logChannel.sendMessage("`" + event.getPlayer().getName() + "` has left the network.");
	}
}