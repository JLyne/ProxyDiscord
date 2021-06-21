package uk.co.notnull.proxydiscord.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import uk.co.notnull.proxydiscord.VerificationResult;

public class ServerConnect {
    private final ProxyDiscord plugin;
    private final VerificationManager verificationManager;

    public ServerConnect(ProxyDiscord plugin) {
        this.plugin = plugin;
        verificationManager = plugin.getVerificationManager();
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onServerConnect(ServerPreConnectEvent e) {
        RegisteredServer server = e.getOriginalServer();
        server = e.getResult().getServer().orElse(server);

        if(verificationManager.isPublicServer(server)) {
            return;
        }

        VerificationResult result = verificationManager.checkVerificationStatus(e.getPlayer());

        if(result.isVerified()) {
            return;
        }

        TextComponent.Builder message;

        switch(result) {
            case NOT_LINKED:
                message = Component.text().content(Messages.get("server-change-not-linked"));
                break;
            case LINKED_NOT_VERIFIED:
                message = Component.text().content(Messages.get("server-change-linked-not-verified"));
                break;
            default:
                message = Component.text().content("An error has occurred.");
        }

        message.color(NamedTextColor.RED);

        if(!verificationManager.getPublicServers().isEmpty()) {
            plugin.getDebugLogger().info("Blocking unverified player " + e.getPlayer().getUsername() + " from joining " + e.getOriginalServer().getServerInfo().getName());

            RegisteredServer linkingServer = verificationManager.getLinkingServer();
            RegisteredServer currentServer = e.getPlayer().getCurrentServer().map(ServerConnection::getServer)
                    .orElse(null);

            if(linkingServer != null && (currentServer == null || !currentServer.equals(linkingServer))) {
                e.setResult(ServerPreConnectEvent.ServerResult.allowed(verificationManager.getLinkingServer()));
            }
        } else {
            plugin.getDebugLogger().info("Disconnecting unverified player " + e.getPlayer().getUsername());
            e.getPlayer().disconnect(message.build());
        }
    }
}
