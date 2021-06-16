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
import uk.co.notnull.proxydiscord.*;

public class JoinLeave {
	private static VerificationManager verificationManager = null;
	private static LoggingManager loggingManager = null;

	private final Logger logger;

    public JoinLeave() {
        this.logger = ProxyDiscord.inst().getLogger();

        verificationManager = ProxyDiscord.inst().getVerificationManager();
		loggingManager = ProxyDiscord.inst().getLoggingManager();
    }

	@Subscribe(order = PostOrder.FIRST)
	public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
		Player player = event.getPlayer();

		loggingManager.logJoin(player);

		VerificationResult result = verificationManager.checkVerificationStatus(player);

		player.sendMessage(Identity.nil(), Component.text(ChatMessages.getMessage("join-welcome"))
				.color(NamedTextColor.GREEN));

		switch(result) {
			case NOT_LINKED:
				logger.info("Unlinked player " + player.getUsername() + " joined");
				break;

			case LINKED_NOT_VERIFIED:
				logger.info("Linked and unverified player " + player.getUsername() + " joined");
				break;

			case VERIFIED:
				logger.info("Verified player " + player.getUsername() + " joined");

				break;
		}

		if(!ProxyDiscord.inst().getDiscord().isConnected()) {
			player.sendMessage(Identity.nil(), Component.text(ChatMessages.getMessage("discord-issues")).color(NamedTextColor.RED));
		}
	}
	
	@Subscribe(order = PostOrder.LAST)
	public void onDisconnect(DisconnectEvent event) {
		Player player = event.getPlayer();

		loggingManager.logLeave(player);
		verificationManager.clearPlayerStatus(player);
	}
}