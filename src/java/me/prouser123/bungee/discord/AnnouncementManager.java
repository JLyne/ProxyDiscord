package me.prouser123.bungee.discord;

import com.google.common.collect.HashBiMap;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.protocol.packet.Chat;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.user.User;

import java.io.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AnnouncementManager {
    private String announcementChannelId;
    private String announcementChannelName = null;
    private Message lastMessage = null;

    AnnouncementManager(String announcementChannelId) {
        this.announcementChannelId = announcementChannelId;

        if(announcementChannelId != null) {
            findChannel();
        }

        Main.inst().getDiscord().getApi().addReconnectListener(event -> {
            if(announcementChannelId != null) {
                findChannel();
            }
        });
    }

    public void sendLatestAnnouncement(CommandSender player) {
        sendAnnouncement(lastMessage, false, player);
    }

    private void sendAnnouncement(Message message) {
        sendAnnouncement(message, true, null);
    }

    private void sendAnnouncement(Message message, boolean isNew, CommandSender player) {
        if(message == null) {
            return;
        }

        lastMessage = message;
        TextComponent announcement;
        String content = "\n" + message.getReadableContent();

        if(isNew) {
            String heading = ChatMessages.getMessage("announcement-new").replace("[channel]", announcementChannelName);
            announcement = new TextComponent(heading);
        } else {
            String heading = ChatMessages.getMessage("announcement-latest").replace("[channel]", announcementChannelName);
            announcement = new TextComponent(heading);
        }

        announcement.setColor(ChatColor.DARK_GREEN);
        announcement.setBold(true);

        TextComponent text = new TextComponent(content.length() > 250 ? content.subSequence(0, 250) + "..." : content);
        text.setColor(ChatColor.GOLD);
        text.setBold(false);

        announcement.addExtra(text);

        if(content.length() > 250) {
            TextComponent readMore = new TextComponent("\n" + ChatMessages.getMessage("announcement-read-more"));
            readMore.setColor(ChatColor.LIGHT_PURPLE);
            readMore.setBold(false);

            announcement.addExtra(readMore);
        }

        if(player != null) {
            player.sendMessage(announcement);
        } else {
            ProxyServer.getInstance().broadcast(announcement);
        }
    }

    private void findChannel() {
        if(announcementChannelId == null) {
            return;
        }

        Optional <TextChannel> announcementChannel = Main.inst().getDiscord().getApi().getTextChannelById(announcementChannelId);

        if(!announcementChannel.isPresent()) {
            Main.inst().getLogger().warning("Unable to find announcement channel. Did you put a valid channel ID in the config?");
            return;
        }

        announcementChannelName = "#" + announcementChannel.toString().replaceAll(".*\\[|].*", "");
        Main.inst().getLogger().info("Announcements enabled for channel: " + announcementChannelName + " (id: " + announcementChannelId + ")");

        announcementChannel.get().addMessageCreateListener(messageCreateEvent -> {
            sendAnnouncement(messageCreateEvent.getMessage());
        });

        announcementChannel.get().getMessages(1).thenAcceptAsync(messages -> {
            if(messages.getNewestMessage().isPresent()) {
                Main.inst().getDebugLogger().info("Retrieved latest announcement");
                lastMessage = messages.getNewestMessage().get();
            }
        }).exceptionally(e -> {
            Main.inst().getLogger().warning("Failed to retrieve latest announcement channel message");
            return null;
        });
    }
}
