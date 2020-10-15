package uk.co.notnull.proxydiscord.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.ServerConnection;
import uk.co.notnull.proxydiscord.ChatMessages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.VerificationManager;
import uk.co.notnull.proxydiscord.VerificationResult;
import uk.co.notnull.proxydiscord.events.PlayerVerifyStateChangeEvent;
import uk.co.notnull.proxyqueues.events.PlayerQueueEvent;

import java.util.Optional;

public class ProxyQueues {
	private static VerificationManager verificationManager;
	private final uk.co.notnull.proxyqueues.ProxyQueues proxyQueues;

    public ProxyQueues(uk.co.notnull.proxyqueues.ProxyQueues plugin) {
		verificationManager = ProxyDiscord.inst().getVerificationManager();
		proxyQueues = plugin;
	}

	@Subscribe
	public void onPlayerJoinQueue(PlayerQueueEvent event) {
		if(event.isCancelled()) {
			return;
		}

        if(verificationManager.isUnverifiedServer(event.getServer())) {
            return;
        }

        VerificationResult result = verificationManager.checkVerificationStatus(event.getPlayer());

        if(result == VerificationResult.VERIFIED) {
            Optional<ServerConnection> currentServer = event.getPlayer().getCurrentServer();

            if(currentServer.isPresent() && verificationManager.isUnverifiedServer(currentServer.get().getServer())) {
                if(proxyQueues.getWaitingServer().isPresent()) {
                    event.getPlayer().createConnectionRequest(proxyQueues.getWaitingServer().get()).fireAndForget();
                }
            }

            return;
        }

		event.setCancelled(true);

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

	@Subscribe(order = PostOrder.NORMAL)
	public void onPlayerVerifyStateChange(PlayerVerifyStateChangeEvent e) {
    	//Remove player from any queue
        if(e.getPreviousState() == VerificationResult.VERIFIED) {
        	proxyQueues.getQueueHandler().clearPlayer(e.getPlayer());
		}
	}
}