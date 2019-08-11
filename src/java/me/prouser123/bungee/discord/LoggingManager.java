package me.prouser123.bungee.discord;

import com.google.common.collect.HashBiMap;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.user.User;

import java.io.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LoggingManager implements Listener {
    private final String loggingChannelId;
    private TextChannel loggingChannel;

    LoggingManager(String loggingChannelId) {
        this.loggingChannelId = loggingChannelId;

        if(loggingChannelId != null) {
            findChannel();
        }

        Main.inst().getProxy().getPluginManager().registerListener(Main.inst(), this);

        Main.inst().getDiscord().getApi().addReconnectListener(event -> {
            if(loggingChannelId != null) {
                findChannel();
            }
        });
    }

    public void logJoin(ProxiedPlayer player) {
        if(loggingChannel != null) {
            loggingChannel.sendMessage("```" + player.getName() + " has joined the network.```");
        }
    }

    public void logLeave(ProxiedPlayer player) {
        if(loggingChannel != null) {
            loggingChannel.sendMessage("```" + player.getName() + " has left the network.```");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(ChatEvent e) {
        if(e.isCancelled()) {
            return;
        }

        if(!(e.getSender() instanceof ProxiedPlayer)) {
            return;
        }

        if(loggingChannel == null) {
            return;
        }

        ProxiedPlayer sender = (ProxiedPlayer) e.getSender();
        Long discordId = Main.inst().getLinkingManager().getLinked(sender);

        if(discordId != null) {
            loggingChannel.sendMessage("```md\n[" + sender.getName() + "](<@!" + discordId.toString() + ">): " + e.getMessage() + "\n```");
        } else {
            loggingChannel.sendMessage("```md\n[" + sender.getName() + "](Unlinked): " + e.getMessage() + "\n```");
        }
    }

    private void findChannel() {
        Optional <TextChannel> loggingChannel = Main.inst().getDiscord().getApi().getTextChannelById(loggingChannelId);

        if(!loggingChannel.isPresent()) {
            Main.inst().getLogger().warning("Unable to find logging channel. Did you put a valid channel ID in the config?");
            this.loggingChannel = null;
            return;
        }

        Main.inst().getLogger().info("Activity logging enabled for channel: #" + loggingChannel.toString().replaceAll(".*\\[|].*", "") + " (id: " + loggingChannelId + ")");
        this.loggingChannel = loggingChannel.get();
    }
}
