package uk.co.notnull.proxydiscord.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.manager.GroupSyncManager;
import uk.co.notnull.proxydiscord.manager.LoggingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import uk.co.notnull.proxydiscord.VerificationResult;

public class JoinLeave {
	private final ProxyDiscord plugin;
	private final Logger logger;

	private static VerificationManager verificationManager;
	private static GroupSyncManager groupSyncManager;
	private static LoggingManager loggingManager;

	public JoinLeave(ProxyDiscord plugin) {
    	this.plugin = plugin;
        this.logger = plugin.getLogger();

        verificationManager = plugin.getVerificationManager();
        groupSyncManager = plugin.getGroupSyncManager();
		loggingManager = plugin.getLoggingManager();
    }

	@Subscribe(order = PostOrder.FIRST)
	public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
		Player player = event.getPlayer();

		loggingManager.logJoin(player);

		player.sendMessage(Identity.nil(), Component.text(Messages.getMessage("join-welcome"))
			.color(NamedTextColor.GREEN));

		if(!plugin.getDiscord().isConnected()) {
			player.sendMessage(Identity.nil(), Component.text(
					Messages.getMessage("discord-issues")).color(NamedTextColor.RED));
		}

		groupSyncManager.syncPlayer(player).thenRun(() -> {
			VerificationResult result = verificationManager.checkVerificationStatus(player);
			logger.info("Player " + player.getUsername() + " joined with verification status " + result);
		});
	}
	
	@Subscribe(order = PostOrder.LAST)
	public void onDisconnect(DisconnectEvent event) {
		Player player = event.getPlayer();

		loggingManager.logLeave(player);
		verificationManager.clearPlayerStatus(player);
	}
}