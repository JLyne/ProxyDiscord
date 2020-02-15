package me.prouser123.bungee.discord.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.glaremasters.deluxequeues.events.PlayerQueueEvent;
import me.prouser123.bungee.discord.*;

public class DeluxeQueues {
	private static VerificationManager verificationManager = null;

	public DeluxeQueues() {
		verificationManager = ProxyDiscord.inst().getVerificationManager();
	}

	@Subscribe
	public void onPlayerJoinQueue(PlayerQueueEvent event) {
		ProxyDiscord.inst().getLogger().info("PlayerQueueEvent");

		if(event.isCancelled()) {
			ProxyDiscord.inst().getLogger().info("Ignoring cancelled event");
			return;
		}

        RegisteredServer unverifiedServer = verificationManager.getUnverifiedServer();

        if(event.getServer().equals(unverifiedServer)) {
        	ProxyDiscord.inst().getLogger().info("Ignoring unverified server");
            return;
        }

        VerificationResult result = verificationManager.checkVerificationStatus(event.getPlayer());

        if(result == VerificationResult.VERIFIED) {
        	ProxyDiscord.inst().getLogger().info("Verified");
            return;
        }

		event.setCancelled(true);

        ProxyDiscord.inst().getLogger().info("Cancelling event");

        switch(result) {
            case NOT_LINKED:
                event.setReason(ChatMessages.getMessage("server-change-not-linked"));
                break;
            case LINKED_NOT_VERIFIED:
                event.setReason(ChatMessages.getMessage("server-change-linked-not-verified"));
                break;
            default:
                event.setReason("An error has occurred.");
        }
	}
}