package me.prouser123.bungee.discord.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.VerificationManager;
import me.prouser123.bungee.discord.VerificationResult;
import me.prouser123.bungee.discord.ChatMessages;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;

import java.util.Optional;

public class ServerConnect {
    private static VerificationManager verificationManager = null;

    public ServerConnect() {
        ServerConnect.verificationManager = Main.inst().getVerificationManager();
    }

    @Subscribe
    public void onServerConnect(ServerPreConnectEvent e) {
        RegisteredServer unverifiedServer = verificationManager.getUnverifiedServer();

        if(e.getOriginalServer().equals(unverifiedServer)) {
            return;
        }

        VerificationResult result = verificationManager.checkVerificationStatus(e.getPlayer());

        if(result == VerificationResult.VERIFIED) {
            return;
        }

        TextComponent message;

        switch(result) {
            case NOT_LINKED:
                message = TextComponent.of(ChatMessages.getMessage("server-change-not-linked"));
                break;
            case LINKED_NOT_VERIFIED:
                message = TextComponent.of(ChatMessages.getMessage("server-change-linked-not-verified"));
                break;
            default:
                message = TextComponent.of("An error has occurred.");
        }

        message.color(TextColor.RED);

        if(unverifiedServer != null) {
            Main.inst().getDebugLogger().info("Blocking unverified player " + e.getPlayer().getUsername() + " from joining " + e.getOriginalServer().getServerInfo().getName());
            Optional<ServerConnection> currentServer = e.getPlayer().getCurrentServer();

            if (currentServer.isPresent() && currentServer.get().getServer().equals(unverifiedServer)) {
                e.setResult(ServerPreConnectEvent.ServerResult.denied());
                e.getPlayer().sendMessage(message);
            } else {
                e.setResult(ServerPreConnectEvent.ServerResult.allowed(unverifiedServer));
            }
        } else {
            Main.inst().getDebugLogger().info("Disconnecting unverified player " + e.getPlayer().getUsername());
            e.getPlayer().disconnect(message);
        }
    }
}
