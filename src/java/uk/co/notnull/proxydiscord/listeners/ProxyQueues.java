package uk.co.notnull.proxydiscord.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import uk.co.notnull.proxydiscord.VerificationResult;
import uk.co.notnull.proxydiscord.events.PlayerVerifyStateChangeEvent;
import uk.co.notnull.proxyqueues.events.PlayerQueueEvent;

import java.util.Optional;

public class ProxyQueues {
	private static VerificationManager verificationManager;
	private final uk.co.notnull.proxyqueues.ProxyQueues proxyQueues;

    public ProxyQueues(ProxyDiscord plugin, uk.co.notnull.proxyqueues.ProxyQueues proxyQueues) {
		verificationManager = plugin.getVerificationManager();
		this.proxyQueues = proxyQueues;
	}

	@Subscribe
	public void onPlayerJoinQueue(PlayerQueueEvent event) {
		if(event.isCancelled()) {
			return;
		}

        if(verificationManager.isPublicServer(event.getServer())) {
            return;
        }

        VerificationResult result = verificationManager.checkVerificationStatus(event.getPlayer());

        if(result.isVerified()) {
            Optional<ServerConnection> currentServer = event.getPlayer().getCurrentServer();

            if(currentServer.isPresent() && verificationManager.isPublicServer(currentServer.get().getServer())) {
                if(proxyQueues.getWaitingServer().isPresent()) {
                    event.getPlayer().createConnectionRequest(proxyQueues.getWaitingServer().get()).fireAndForget();
                }
            }

            return;
        }

		event.setCancelled(true);

        RegisteredServer linkingServer = verificationManager.getLinkingServer();
		RegisteredServer currentServer = event.getPlayer().getCurrentServer().map(ServerConnection::getServer)
				.orElse(null);

        if(linkingServer != null && (currentServer == null || !currentServer.equals(linkingServer))) {
        	event.getPlayer().createConnectionRequest(linkingServer).fireAndForget();
		}

        switch(result) {
            case NOT_LINKED:
                event.setReason(Messages.getMessage("server-change-not-linked"));
                break;
            case LINKED_NOT_VERIFIED:
                event.setReason(Messages.getMessage("server-change-linked-not-verified"));
                break;
            default:
                event.setReason("An error has occurred.");
        }
	}

	@Subscribe(order = PostOrder.NORMAL)
	public void onPlayerVerifyStateChange(PlayerVerifyStateChangeEvent e) {
    	//Remove player from any queue
        if(!e.getState().isVerified()) {
        	proxyQueues.getQueueHandler().clearPlayer(e.getPlayer());
		}
	}
}