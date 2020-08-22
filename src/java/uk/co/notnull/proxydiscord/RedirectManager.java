package uk.co.notnull.proxydiscord;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.events.PlayerVerifyStateChangeEvent;

import java.util.*;

public class RedirectManager {
    private final VerificationManager verificationManager;
    private final HashMap<UUID, RegisteredServer> destinations;

    private final ProxyServer proxy;
    private final Logger logger;
    private final RegisteredServer unverifiedServer;
    private final RegisteredServer defaultVerifiedServer;

    public RedirectManager() {
        this.proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();
        this.verificationManager = ProxyDiscord.inst().getVerificationManager();

        unverifiedServer = verificationManager.getUnverifiedServer();
        defaultVerifiedServer = verificationManager.getDefaultVerifiedServer();

        destinations = new HashMap<>();
        proxy.getEventManager().register(ProxyDiscord.inst(), this);
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPlayerConnect(ServerPreConnectEvent event) {
        Optional<RegisteredServer> redirectServer = event.getResult().getServer();

        if(redirectServer.isPresent() && redirectServer.get().equals(unverifiedServer)) {
            if(!event.getOriginalServer().equals(unverifiedServer)) {
                destinations.put(event.getPlayer().getUniqueId(), event.getOriginalServer());
            }
        } else {
            destinations.remove(event.getPlayer().getUniqueId());
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayerVerifyStateChange(PlayerVerifyStateChangeEvent event) {
        Player player = event.getPlayer();

        switch(event.getState()) {
            case VERIFIED:
                sendToDestinationServer(player);
                break;

            case LINKED_NOT_VERIFIED:
                if(event.getPreviousState() == VerificationResult.VERIFIED) {
                    sendToUnverifiedServer(player, ChatMessages.getMessage("verification-lost-role"));
                }

                break;
            case NOT_LINKED:
                if(event.getPreviousState() == VerificationResult.VERIFIED) {
                    sendToUnverifiedServer(player, ChatMessages.getMessage("verification-lost-unlinked"));
                }
        }
    }

    private void sendToUnverifiedServer(Player player, String message) {
        TextComponent.Builder component = TextComponent.builder();

        component.color(NamedTextColor.RED).content(message);

        if(unverifiedServer == null) {
            ProxyDiscord.inst().getDebugLogger().info("No unverified server defined. Kicking " + player.getUsername());
            player.disconnect(component.build());

            return;
        }

        Optional<ServerConnection> currentServer = player.getCurrentServer();

        if(currentServer.isPresent() && !currentServer.get().getServer().equals(unverifiedServer)) {
            ProxyDiscord.inst().getDebugLogger().info("Moving " + player.getUsername() + " to " + unverifiedServer.getServerInfo().getName());

            player.createConnectionRequest(unverifiedServer).connect().thenAccept(result -> {
                if(result.isSuccessful()) {
                    String text = ChatMessages.getMessage("verification-lost-moved");
                    TextComponent extra = TextComponent.of(" " + text.replace("[server]", unverifiedServer.getServerInfo().getName()));
                    component.append(extra);

                    player.sendMessage(component.build());
                } else {
                    ProxyDiscord.inst().getDebugLogger().info("Failed to move " + player.getUsername() + " to " + unverifiedServer.getServerInfo().getName() + ". Kicking.");
                    player.disconnect(component.build());
                }
            });
        } else {
            player.sendMessage(component.build());
        }
    }

    private void sendToDestinationServer(Player player) {
        player.getCurrentServer().ifPresent(connection -> {
            if(!connection.getServer().equals(unverifiedServer)) {
                return;
            }

            if(destinations.containsKey(player.getUniqueId())) {
                player.createConnectionRequest(destinations.get(player.getUniqueId())).fireAndForget();
            } else if(defaultVerifiedServer != null) {
                player.createConnectionRequest(defaultVerifiedServer).fireAndForget();
            }
        });
    }
}
