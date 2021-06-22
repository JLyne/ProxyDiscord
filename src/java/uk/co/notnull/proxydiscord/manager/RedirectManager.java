package uk.co.notnull.proxydiscord.manager;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.events.PlayerVerifyStateChangeEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RedirectManager {
    private final ProxyDiscord plugin;
    private final VerificationManager verificationManager;
    private final ConcurrentHashMap<UUID, RegisteredServer> destinations;

    public RedirectManager(ProxyDiscord plugin) {
        ProxyServer proxy = plugin.getProxy();

        this.plugin = plugin;
        this.verificationManager = plugin.getVerificationManager();

        destinations = new ConcurrentHashMap<>();
        proxy.getEventManager().register(plugin, this);
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPlayerConnect(ServerPreConnectEvent event) {
        Optional<RegisteredServer> redirectServer = event.getResult().getServer();

        if(redirectServer.isPresent() && verificationManager.isPublicServer(redirectServer.get())) {
            if(!verificationManager.isPublicServer(event.getOriginalServer())) {
                destinations.put(event.getPlayer().getUniqueId(), event.getOriginalServer());
            }
        } else {
            destinations.remove(event.getPlayer().getUniqueId());
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayerVerifyStateChange(PlayerVerifyStateChangeEvent event) {
        Player player = event.getPlayer();
        RegisteredServer currentServer = player.getCurrentServer().map(ServerConnection::getServer).orElse(null);

        switch(event.getState()) {
            case VERIFIED:
            case NOT_REQUIRED:
            case BYPASSED:
                sendToDestinationServer(player);
                break;

            case LINKED_NOT_VERIFIED:
                if(event.getPreviousState().isVerified() || !verificationManager.isPublicServer(currentServer)) {
                    sendToLinkingServer(player, Messages.get("verification-lost-role"));
                } else {
                    destinations.computeIfPresent(player.getUniqueId(), (UUID uuid, RegisteredServer server) -> {
                        if(verificationManager.isPublicServer(server)) {
                            sendToDestinationServer(player);
                        }

                        return server;
                    });
                }

                break;
            case NOT_LINKED:
                if(event.getPreviousState().isVerified() || !verificationManager.isPublicServer(currentServer)) {
                    sendToLinkingServer(player, Messages.get("verification-lost-unlinked"));
                }
        }
    }

    private void sendToLinkingServer(Player player, String message) {
        TextComponent.Builder component = Component.text();

        component.color(NamedTextColor.RED).content(message);

        if(verificationManager.getPublicServers().isEmpty()) {
            plugin.getDebugLogger().info("No public servers defined. Kicking " + player.getUsername());
            player.disconnect(component.build());

            return;
        }

        Optional<ServerConnection> currentServer = player.getCurrentServer();
        RegisteredServer linkingServer = verificationManager.getLinkingServer();

        if(currentServer.isPresent() && linkingServer != null && !verificationManager.isPublicServer(currentServer.get().getServer())) {
            plugin.getDebugLogger().info("Moving " + player.getUsername() + " to " + linkingServer.getServerInfo().getName());

            destinations.put(player.getUniqueId(), currentServer.get().getServer());

            player.createConnectionRequest(linkingServer).connect().thenAccept(result -> {
                if(result.isSuccessful()) {
                    String text = Messages.get("verification-lost-moved");
                    TextComponent extra = Component.text(" " + text.replace("[server]", linkingServer.getServerInfo().getName()));
                    component.append(extra);

                    player.sendMessage(Identity.nil(), component.build());
                } else {
                    plugin.getDebugLogger().info("Failed to move " + player.getUsername() + " to " + linkingServer.getServerInfo().getName() + ". Kicking.");
                    player.disconnect(component.build());
                }
            });
        } else {
            player.sendMessage(Identity.nil(), component.build());
        }
    }

    private void sendToDestinationServer(Player player) {
        player.getCurrentServer().ifPresent(connection -> {
            RegisteredServer defaultVerifiedServer = verificationManager.getDefaultVerifiedServer();

            if(!verificationManager.isLinkingServer(connection.getServer())) {
                return;
            }

            destinations.compute(player.getUniqueId(), (UUID uuid, RegisteredServer server) -> {
                if(server != null) {
                    player.createConnectionRequest(destinations.get(player.getUniqueId())).fireAndForget();
                } else if(defaultVerifiedServer != null) {
                    player.createConnectionRequest(defaultVerifiedServer).fireAndForget();
                }

                return null;
            });
        });
    }
}
