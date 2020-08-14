package uk.co.notnull.proxydiscord.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import uk.co.notnull.proxydiscord.ChatMessages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.VerificationManager;
import uk.co.notnull.proxydiscord.VerificationResult;
import uk.co.notnull.proxyqueues.events.PlayerQueueEvent;

import java.util.Optional;

public class ProxyQueues {
	private static VerificationManager verificationManager = null;
    private final ProxyServer proxy;
    private uk.co.notnull.proxyqueues.ProxyQueues proxyQueues = null;

    public ProxyQueues() {
		verificationManager = ProxyDiscord.inst().getVerificationManager();
		proxy = ProxyDiscord.inst().getProxy();

		Optional<PluginContainer> plugin = proxy.getPluginManager().getPlugin("deluxequeues");

        plugin.ifPresent(pluginContainer -> proxyQueues = (uk.co.notnull.proxyqueues.ProxyQueues) pluginContainer.getInstance().orElse(null));
	}

	@Subscribe
	public void onPlayerJoinQueue(PlayerQueueEvent event) {
		if(event.isCancelled()) {
			return;
		}

        RegisteredServer unverifiedServer = verificationManager.getUnverifiedServer();

        if(event.getServer().equals(unverifiedServer)) {
            return;
        }

        VerificationResult result = verificationManager.checkVerificationStatus(event.getPlayer());

        if(result == VerificationResult.VERIFIED) {
            Optional<ServerConnection> currentServer = event.getPlayer().getCurrentServer();

            if(currentServer.isPresent() && currentServer.get().getServer().equals(verificationManager.getUnverifiedServer())) {
                if(proxyQueues != null && proxyQueues.getWaitingServer().isPresent()) {
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
}