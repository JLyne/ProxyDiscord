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

package uk.co.notnull.proxydiscord.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import uk.co.notnull.proxydiscord.api.VerificationResult;

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

        Component message;

        switch(result) {
            case NOT_LINKED:
                message = Messages.getComponent("server-change-not-linked");
                break;
            case LINKED_NOT_VERIFIED:
                message = Messages.getComponent("server-change-linked-not-verified");
                break;
            default:
                //TODO Make configurable
                message = Component.text("An error has occurred.").color(NamedTextColor.RED);
        }

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
            e.getPlayer().disconnect(message);
        }
    }
}
