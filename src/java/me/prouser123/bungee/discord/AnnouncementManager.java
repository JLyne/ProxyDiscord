package me.prouser123.bungee.discord;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.slf4j.Logger;

import java.util.Optional;

public class AnnouncementManager {
    private String announcementChannelId;
    private String announcementChannelName = null;
    private Message lastMessage = null;

    private final ProxyServer proxy;
    private final Logger logger;

    public AnnouncementManager(String announcementChannelId) {
        this.proxy = Main.inst().getProxy();
        this.logger = Main.inst().getLogger();

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

    public void sendLatestAnnouncement(Player player) {
        sendAnnouncement(lastMessage, false, player);
    }

    private void sendAnnouncement(Message message) {
        sendAnnouncement(message, true, null);
    }

    private void sendAnnouncement(Message message, boolean isNew, Player player) {
        if(message == null) {
            return;
        }

        lastMessage = message;
        TextComponent.Builder announcement;
        String content = "\n" + message.getReadableContent();

        if(isNew) {
            String heading = ChatMessages.getMessage("announcement-new").replace("[channel]", announcementChannelName);
            announcement = TextComponent.builder().content(heading);
        } else {
            String heading = ChatMessages.getMessage("announcement-latest").replace("[channel]", announcementChannelName);
            announcement = TextComponent.builder().content(heading);
        }

        announcement.color(TextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true);

        TextComponent text = TextComponent.of(content.length() > 250 ? content.subSequence(0, 250) + "..." : content)
                .color(TextColor.GOLD)
                .decoration(TextDecoration.BOLD, false);

        announcement.append(text);

        if(content.length() > 250) {
            TextComponent readMore = TextComponent.of("\n" + ChatMessages.getMessage("announcement-read-more"))
                    .color(TextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, false);

            announcement.append(readMore);
        }

        if(player != null) {
            player.sendMessage(announcement.build());
        } else {
            proxy.broadcast(announcement.build());
        }
    }

    private void findChannel() {
        if(announcementChannelId == null) {
            return;
        }

        Optional <TextChannel> announcementChannel = Main.inst().getDiscord().getApi().getTextChannelById(announcementChannelId);

        if(!announcementChannel.isPresent()) {
            logger.warn("Unable to find announcement channel. Did you put a valid channel ID in the config?");
            return;
        }

        announcementChannelName = "#" + announcementChannel.toString().replaceAll(".*\\[|].*", "");
        logger.info("Announcements enabled for channel: " + announcementChannelName + " (id: " + announcementChannelId + ")");

        announcementChannel.get().addMessageCreateListener(messageCreateEvent -> sendAnnouncement(messageCreateEvent.getMessage()));

        announcementChannel.get().getMessages(1).thenAcceptAsync(messages -> {
            if(messages.getNewestMessage().isPresent()) {
                Main.inst().getDebugLogger().info("Retrieved latest announcement");
                lastMessage = messages.getNewestMessage().get();
            }
        }).exceptionally(e -> {
            logger.warn("Failed to retrieve latest announcement channel message");
            return null;
        });
    }
}
