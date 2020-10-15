package uk.co.notnull.proxydiscord.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import uk.co.notnull.proxydiscord.*;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

public class ServerConnect {
    private static VerificationManager verificationManager = null;

    public ServerConnect() {
        ServerConnect.verificationManager = ProxyDiscord.inst().getVerificationManager();
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onServerConnect(ServerPreConnectEvent e) {
        RegisteredServer server = e.getOriginalServer();
        server = e.getResult().getServer().orElse(server);

        if(verificationManager.isUnverifiedServer(server)) {
            return;
        }

        VerificationResult result = verificationManager.checkVerificationStatus(e.getPlayer());

        if(result == VerificationResult.VERIFIED) {
            return;
        }

        TextComponent.Builder message;

        switch(result) {
            case NOT_LINKED:
                message = TextComponent.builder().content(ChatMessages.getMessage("server-change-not-linked"));
                break;
            case LINKED_NOT_VERIFIED:
                message = TextComponent.builder().content(ChatMessages.getMessage("server-change-linked-not-verified"));
                break;
            default:
                message = TextComponent.builder().content("An error has occurred.");
        }

        message.color(NamedTextColor.RED);

        if(!verificationManager.getUnverifiedServers().isEmpty()) {
            ProxyDiscord.inst().getDebugLogger().info("Blocking unverified player " + e.getPlayer().getUsername() + " from joining " + e.getOriginalServer().getServerInfo().getName());
            Optional<ServerConnection> currentServer = e.getPlayer().getCurrentServer();

            if (currentServer.isPresent() && verificationManager.isUnverifiedServer(currentServer.get().getServer())) {
                e.setResult(ServerPreConnectEvent.ServerResult.denied());
                e.getPlayer().sendMessage(message.build());
            } else {
                e.setResult(ServerPreConnectEvent.ServerResult.allowed(unverifiedServer));
            }

            //
        } else {
            ProxyDiscord.inst().getDebugLogger().info("Disconnecting unverified player " + e.getPlayer().getUsername());
            e.getPlayer().disconnect(message.build());
        }
    }
}
