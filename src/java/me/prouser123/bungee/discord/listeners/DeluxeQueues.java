package me.prouser123.bungee.discord.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.glaremasters.deluxequeues.events.PlayerQueueEvent;
import me.prouser123.bungee.discord.*;

public class DeluxeQueues {
	private static VerificationManager verificationManager = null;

	public DeluxeQueues() {
		verificationManager = Main.inst().getVerificationManager();
	}

	@Subscribe
	public void onPlayerJoinQueue(PlayerQueueEvent event) {
		Main.inst().getLogger().info("PlayerQueueEvent");

		if(event.isCancelled()) {
			Main.inst().getLogger().info("Ignoring cancelled event");
			return;
		}

        RegisteredServer unverifiedServer = verificationManager.getUnverifiedServer();

        if(event.getServer().equals(unverifiedServer)) {
        	Main.inst().getLogger().info("Ignoring unverified server");
            return;
        }

        VerificationResult result = verificationManager.checkVerificationStatus(event.getPlayer());

        if(result == VerificationResult.VERIFIED) {
        	Main.inst().getLogger().info("Verified");
            return;
        }

		event.setCancelled(true);

        Main.inst().getLogger().info("Cancelling event");

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