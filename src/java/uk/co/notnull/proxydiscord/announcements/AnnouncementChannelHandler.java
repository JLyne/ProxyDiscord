package uk.co.notnull.proxydiscord.announcements;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.listener.message.MessageDeleteListener;
import org.javacord.api.listener.message.MessageEditListener;
import org.javacord.api.util.event.ListenerManager;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ChatMessages;
import uk.co.notnull.proxydiscord.ProxyDiscord;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AnnouncementChannelHandler {
	private final ProxyServer proxy;
	private final Logger logger;

	private final String channelId;
	private final Set<RegisteredServer> servers;

	private String channelName;

	private ListenerManager<MessageCreateListener> createListener;
	private ListenerManager<MessageDeleteListener> deleteListener;
	private ListenerManager<MessageEditListener> editListener;
	private final Set<UUID> sentLatestMessage;

	private Message lastMessage;

	private final LegacyComponentSerializer serializer = LegacyComponentSerializer.builder()
			.extractUrls(Style.style()
								 .decoration(TextDecoration.UNDERLINED, TextDecoration.State.TRUE)
								 .color(NamedTextColor.BLUE).build())
			.build();

	public AnnouncementChannelHandler(String channelId) {
		this(channelId, Collections.emptyList());
	}

	public AnnouncementChannelHandler(String channelId, List<String> serverList) {
		this.channelId = channelId;

		this.proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();
        this.sentLatestMessage = ConcurrentHashMap.newKeySet();

		if(serverList.isEmpty()) {
        	this.servers = null;
		} else {
        	final Set<RegisteredServer> servers = new HashSet<>();

			serverList.forEach(name -> {
				Optional<RegisteredServer> server = ProxyDiscord.inst().getProxy().getServer(name);

				if(server.isPresent()) {
					servers.add(server.get());
				} else {
					logger.warn("Unknown server " + name + " for announcement channel " + channelId);
				}
			});

			this.servers = servers;
		}

		ProxyDiscord.inst().getProxy().getEventManager().register(ProxyDiscord.inst(), this);

		createListeners();
		getLatestMessage();
	}

	private TextChannel getChannel() {
		Optional<TextChannel> announcementChannel = ProxyDiscord.inst().getDiscord().getApi()
                    .getTextChannelById(channelId);

		if(announcementChannel.isEmpty()) {
			throw new RuntimeException("Channel " + channelId + " not found");
		}

		channelName = "#" + announcementChannel.toString().replaceAll(".*\\[|].*", "");

		return announcementChannel.get();
	}

	private void createListeners() {
		TextChannel channel = getChannel();

		logger.info("Announcements enabled for channel: " + channelName + " (id: " + channelId + ")");
		createListener = channel.addMessageCreateListener(messageCreateEvent ->
																sendAnnouncement(messageCreateEvent.getMessage()));

		editListener = channel.addMessageEditListener(messageEditEvent -> {
			if(messageEditEvent.getMessageId() == lastMessage.getId() && messageEditEvent.getMessage().isPresent()) {
				lastMessage = messageEditEvent.getMessage().get();
			}
		});

		deleteListener = channel.addMessageDeleteListener(messageDeleteEvent -> {
			if(messageDeleteEvent.getMessageId() == lastMessage.getId()) {
				lastMessage = null;
				getLatestMessage();
			}
		});
	}

	private void getLatestMessage() {
		TextChannel channel = getChannel();

		channel.getMessages(1).thenAcceptAsync(messages -> {
			if(messages.getNewestMessage().isPresent()) {
				ProxyDiscord.inst().getDebugLogger().info("Retrieved latest announcement for " + channelName);
				lastMessage = messages.getNewestMessage().get();
			}
		}).exceptionally(e -> {
			logger.warn("Failed to retrieve latest announcement for " + channelName);
			return null;
		});
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
            heading = ChatMessages.getMessage("announcement-new").replace("[channel]", channelName);
        } else {
            heading = ChatMessages.getMessage("announcement-latest").replace("[channel]", channelName);
        }

        announcement = Component.text().content(heading)
                .color(NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true);

        String text = content.length() > 250 ? content.subSequence(0, 250) + "..." : content;

        announcement.append(serializer.deserialize(text).color(NamedTextColor.GOLD)
				.decoration(TextDecoration.BOLD, false));

//        Component component = new MineDown(text)
//                .disable(MineDownParser.Option.ADVANCED_FORMATTING)
//                .disable(MineDownParser.Option.LEGACY_COLORS).toComponent()
//                .color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, false);
//        announcement.append(component);

        if(content.length() > 250) {
            TextComponent readMore = Component.text("\n" + ChatMessages.getMessage("announcement-read-more"))
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, false);

            announcement.append(readMore);
        }

        Component result = announcement.build();

        if(player != null) {
			sentLatestMessage.add(player.getUniqueId());
            player.sendMessage(Identity.nil(), announcement.build());
        } else if(servers != null) {
        	sentLatestMessage.clear();

        	List<Player> recipients = proxy.getAllPlayers().stream().filter(p -> {
        		return p.getCurrentServer().isPresent() && servers.contains(p.getCurrentServer().get().getServer());
			}).collect(Collectors.toList());

            recipients.forEach(p ->{
            	sentLatestMessage.add(p.getUniqueId());
            	p.sendMessage(Identity.nil(), result);
			});
        } else {
        	sentLatestMessage.clear();
        	RegisteredServer linkingServer = ProxyDiscord.inst().getVerificationManager().getLinkingServer();

        	List<Player> recipients = proxy.getAllPlayers().stream().filter(p -> {
        		return p.getCurrentServer().isPresent()
						&& (linkingServer == null || !p.getCurrentServer().get().getServer().equals(linkingServer));
			}).collect(Collectors.toList());

        	recipients.forEach(p -> {
            	sentLatestMessage.add(p.getUniqueId());
        		p.sendMessage(Identity.nil(), result);
			});
		}
    }

    public void remove() {
		createListener.remove();
		editListener.remove();
		deleteListener.remove();
		proxy.getEventManager().unregisterListener(ProxyDiscord.inst(), this);
	}

	@Subscribe(order = PostOrder.LATE)
	public void onPlayerConnected(ServerPostConnectEvent event) {
		Player player = event.getPlayer();

		if(player.getCurrentServer().isEmpty()) {
			return;
		}

		RegisteredServer server = player.getCurrentServer().get().getServer();

		if(ProxyDiscord.inst().getVerificationManager().getLinkingServer().equals(server)) {
			return;
		}

		if(sentLatestMessage.contains(player.getUniqueId())) {
			return;
		}

		if(servers != null) {
			if(servers.contains(server)) {
				sendAnnouncement(lastMessage, false, player);
			}
		} else if(!sentLatestMessage.contains(player.getUniqueId())) {
			sendAnnouncement(lastMessage, false, player);
		}
	}

	@Subscribe(order = PostOrder.LATE)
	public void onPlayerDisconnected(DisconnectEvent event) {
		sentLatestMessage.remove(event.getPlayer().getUniqueId());
	}
}
