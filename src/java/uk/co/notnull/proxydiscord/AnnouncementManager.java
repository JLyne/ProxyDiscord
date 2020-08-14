package uk.co.notnull.proxydiscord;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;
import org.slf4j.Logger;

import java.util.Optional;

public class AnnouncementManager {
    private final String announcementChannelId;
    private String announcementChannelName = null;
    private Message lastMessage = null;
    private ListenerManager<MessageCreateListener> listener = null;

    private final ProxyServer proxy;
    private final Logger logger;

    public AnnouncementManager(String announcementChannelId) {
        this.proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();

        this.announcementChannelId = announcementChannelId;

        if(announcementChannelId != null) {
            findChannel();
        }

        ProxyDiscord.inst().getDiscord().getApi().addReconnectListener(event -> {
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
        String heading;

        if(isNew) {
            heading = ChatMessages.getMessage("announcement-new").replace("[channel]", announcementChannelName);
        } else {
            heading = ChatMessages.getMessage("announcement-latest").replace("[channel]", announcementChannelName);
        }

        announcement = TextComponent.builder().content(heading)
                .color(NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true);

        TextComponent text = TextComponent.of(content.length() > 250 ? content.subSequence(0, 250) + "..." : content)
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, false);

        announcement.append(text);

        if(content.length() > 250) {
            TextComponent readMore = TextComponent.of("\n" + ChatMessages.getMessage("announcement-read-more"))
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, false);

            announcement.append(readMore);
        }

        if(player != null) {
            player.sendMessage(announcement.build());
        } else {
            proxy.sendMessage(announcement.build());
        }
    }

    private void findChannel() {
        if(announcementChannelId == null) {
            return;
        }

        Optional <TextChannel> announcementChannel = ProxyDiscord.inst().getDiscord().getApi()
                .getTextChannelById(announcementChannelId);

        if(!announcementChannel.isPresent()) {
            if(listener != null) {
                listener.remove();
            }

            logger.warn("Unable to find announcement channel. Did you put a valid channel ID in the config?");
            return;
        }

        announcementChannelName = "#" + announcementChannel.toString().replaceAll(".*\\[|].*", "");
        logger.info("Announcements enabled for channel: " + announcementChannelName + " (id: " + announcementChannelId + ")");

        if(listener == null) {
            listener = announcementChannel.get()
                    .addMessageCreateListener(messageCreateEvent -> sendAnnouncement(messageCreateEvent.getMessage()));
        }

        announcementChannel.get().getMessages(1).thenAcceptAsync(messages -> {
            if(messages.getNewestMessage().isPresent()) {
                ProxyDiscord.inst().getDebugLogger().info("Retrieved latest announcement");
                lastMessage = messages.getNewestMessage().get();
            }
        }).exceptionally(e -> {
            logger.warn("Failed to retrieve latest announcement channel message");
            return null;
        });
    }
}
