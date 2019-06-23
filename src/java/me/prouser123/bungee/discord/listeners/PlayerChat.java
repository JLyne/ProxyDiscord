package me.prouser123.bungee.discord.listeners;

import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.javacord.api.entity.channel.TextChannel;

public class PlayerChat implements Listener {
    private static TextChannel logChannel = null;
    private static LinkingManager linkingManager = null;

    public PlayerChat(TextChannel logChannel) {
        PlayerChat.logChannel = logChannel;
        PlayerChat.linkingManager = Main.inst().getLinkingManager();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(ChatEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer sender = (ProxiedPlayer) e.getSender();
        Long discordId = linkingManager.getLinked(sender);

        if(discordId != null) {
            logChannel.sendMessage(sender.getName() + " (<@!" + discordId.toString() + ">): " + e.getMessage());
        } else {
            logChannel.sendMessage(sender.getName() + ": " + e.getMessage());
        }
    }
}
