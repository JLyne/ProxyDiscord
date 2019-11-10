package me.prouser123.bungee.discord.listeners;

import me.glaremasters.deluxequeues.events.PlayerQueueEvent;
import me.prouser123.bungee.discord.*;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class DeluxeQueues implements Listener {
	private static VerificationManager verificationManager = null;

	public DeluxeQueues() {
		verificationManager = Main.inst().getVerificationManager();
	}

	@EventHandler(priority = EventPriority.LOW)
	public void nPlayerJoinQueue(PlayerQueueEvent event) {
		Main.inst().getLogger().info("PlayerQueueEvent");

		if(event.isCancelled()) {
			Main.inst().getLogger().info("Ignoring cancelled event");
			return;
		}

        if(event.getPlayer().getXUID() != null) {
			Main.inst().getLogger().info("Ignoring bedrock player");
            return;
        }

        ServerInfo unverifiedServer = verificationManager.getUnverifiedServer();

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