package me.prouser123.bungee.discord.listeners;

import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.VerificationManager;
import me.prouser123.bungee.discord.VerificationResult;
import me.prouser123.bungee.discord.ChatMessages;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.protocol.Protocol;
import net.md_5.bungee.protocol.ProtocolConstants;

public class ServerConnect implements Listener {
    private static VerificationManager verificationManager = null;

    public ServerConnect() {
        ServerConnect.verificationManager = Main.inst().getVerificationManager();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onServerConnect(ServerConnectEvent e) {
        if (e.isCancelled()) return;

        //Disable linking for bedrock users
        if(e.getPlayer().getXUID() != null) {
            Main.inst().getLogger().info("Bedrock player " + e.getPlayer().getName() + " switching servers. Not checking link status.");

            return;
        }

        ServerInfo unverifiedServer = verificationManager.getUnverifiedServer();

        if(e.getTarget().equals(unverifiedServer)) {
            return;
        }

        VerificationResult result = verificationManager.checkVerificationStatus(e.getPlayer());

        if(result == VerificationResult.VERIFIED) {
            return;
        }

        TextComponent message;

        switch(result) {
            case NOT_LINKED:
                message = new TextComponent(ChatMessages.getMessage("server-change-not-linked"));
                break;
            case LINKED_NOT_VERIFIED:
                message = new TextComponent(ChatMessages.getMessage("server-change-linked-not-verified"));
                break;
            default:
                message = new TextComponent("An error has occurred.");
        }

        message.setColor(ChatColor.RED);

        if(unverifiedServer != null) {
            Main.inst().getDebugLogger().info("Blocking unverified player " + e.getPlayer().getName() + " from joining " + e.getTarget().getName());
            Server currentServer = e.getPlayer().getServer();

            if (currentServer != null && currentServer.getInfo().equals(unverifiedServer)) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(message);
            } else {
                e.setTarget(unverifiedServer);
            }
        } else {
            Main.inst().getDebugLogger().info("Disconnecting unverified player " + e.getPlayer().getName());
            e.getPlayer().disconnect(message);
            e.setCancelled(true);
        }
    }
}
