/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2021 James Lyne
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package uk.co.notnull.proxydiscord.manager;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.api.events.PlayerVerifyStateChangeEvent;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RedirectManager {
    private final ProxyDiscord plugin;
    private final VerificationManager verificationManager;
    private final ConcurrentHashMap<UUID, RegisteredServer> destinations;

    public RedirectManager(ProxyDiscord plugin) {
        this.plugin = plugin;
        this.verificationManager = plugin.getVerificationManager();

        destinations = new ConcurrentHashMap<>();
    }

    @Subscribe(priority = Short.MIN_VALUE + 1)
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

    @Subscribe()
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
                if(event.getPreviousState().isVerified()) {
                    sendToLinkingServer(player, Messages.getComponent("verification-lost-role"));
                } else if(!verificationManager.isPublicServer(currentServer)) {
                    sendToLinkingServer(player, Messages.getComponent("server-change-linked-not-verified"));
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
                if(event.getPreviousState().isVerified()) {
                    sendToLinkingServer(player, Messages.getComponent("verification-lost-unlinked"));
                } else if(!verificationManager.isPublicServer(currentServer)) {
                    sendToLinkingServer(player, Messages.getComponent("server-change-linked-not-verified"));
                }
        }
    }

    private void sendToLinkingServer(Player player, Component message) {
        if(verificationManager.getPublicServers().isEmpty()) {
            plugin.getDebugLogger().info("No public servers defined. Kicking " + player.getUsername());
            player.disconnect(message);

            return;
        }

        Optional<ServerConnection> currentServer = player.getCurrentServer();
        RegisteredServer linkingServer = verificationManager.getLinkingServer();

        if(currentServer.isPresent() && linkingServer != null && !verificationManager.isPublicServer(currentServer.get().getServer())) {
            plugin.getDebugLogger().info("Moving " + player.getUsername() + " to " + linkingServer.getServerInfo().getName());

            destinations.put(player.getUniqueId(), currentServer.get().getServer());

            player.createConnectionRequest(linkingServer).connect().thenAccept(result -> {
                if(result.isSuccessful()) {
                    Component extra = Messages.getComponent(
                            "verification-lost-moved",
                            Collections.singletonMap("server", linkingServer.getServerInfo().getName()),
                            Collections.emptyMap());

                    player.sendMessage(message.append(Component.newline()).append(extra));
                } else {
                    plugin.getDebugLogger().info("Failed to move " + player.getUsername() + " to " + linkingServer.getServerInfo().getName() + ". Kicking.");
                    player.disconnect(message);
                }
            });
        } else {
            player.sendMessage(message);
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
