package uk.co.notnull.proxydiscord.api.manager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import uk.co.notnull.proxydiscord.api.VerificationResult;

import java.util.Set;

public interface VerificationManager {
	VerificationResult checkVerificationStatus(Player player);

    VerificationResult checkVerificationStatus(Long discordId);

    Set<RegisteredServer> getPublicServers();

    boolean isPublicServer(RegisteredServer server);

    RegisteredServer getDefaultVerifiedServer();

    RegisteredServer getLinkingServer();

    boolean isLinkingServer(RegisteredServer server);
}
