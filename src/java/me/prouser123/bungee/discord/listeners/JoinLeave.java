package me.prouser123.bungee.discord.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.prouser123.bungee.discord.*;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.UUID;

public class JoinLeave {
	private static VerificationManager verificationManager = null;
	private static KickManager kickManager = null;
	private static LoggingManager loggingManager = null;

	private HashMap<UUID, Boolean> firstJoin;

	private final ProxyServer proxy;
    private final Logger logger;

    public JoinLeave() {
        this.proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();

        verificationManager = ProxyDiscord.inst().getVerificationManager();
		kickManager = ProxyDiscord.inst().getKickManager();
		loggingManager = ProxyDiscord.inst().getLoggingManager();
		firstJoin = new HashMap<>();
        logger.info("Hello there, it's a test plugin I made!");
    }

	@Subscribe
	public void onPostLogin(PostLoginEvent event) {
		firstJoin.put(event.getPlayer().getUniqueId(), true);
	}

	@Subscribe
	public void onServerConnected(ServerPreConnectEvent event) {
		Player player = event.getPlayer();

		if(!firstJoin.get(player.getUniqueId())) {
			return;
		}

		firstJoin.put(player.getUniqueId(), false);
		loggingManager.logJoin(player);

		String text;
		VerificationResult result = verificationManager.checkVerificationStatus(player);

		player.sendMessage(TextComponent.of(ChatMessages.getMessage("join-welcome"))
				.color(TextColor.GREEN));

		switch(result) {
			case NOT_LINKED:
				logger.info("Unlinked player " + player.getUsername() + " joined");

				text = ChatMessages.getMessage("join-not-linked");
				player.sendMessage(TextComponent.of(text).color(TextColor.YELLOW));

				kickManager.addPlayer(player);
				break;

			case LINKED_NOT_VERIFIED:
				logger.info("Linked and unverified player " + player.getUsername() + " joined");

				text = ChatMessages.getMessage("join-linked-not-verified");
				player.sendMessage(TextComponent.of(text).color(TextColor.YELLOW));

				kickManager.addPlayer(player);
				break;

			case VERIFIED:
				logger.info("Verified player " + player.getUsername() + " joined");
				ProxyDiscord.inst().getAnnouncementManager().sendLatestAnnouncement(player);

				break;
		}

		if(!ProxyDiscord.inst().getDiscord().isConnected()) {
			player.sendMessage(TextComponent.of(ChatMessages.getMessage("discord-issues")).color(TextColor.RED));
		}
	}
	
	@Subscribe
	public void onDisconnect(DisconnectEvent event) {
		Player player = event.getPlayer();

		if(!firstJoin.get(player.getUniqueId())) {
			loggingManager.logLeave(player);
		}

		firstJoin.remove(player.getUniqueId());
		kickManager.removePlayer(player);
	}
}