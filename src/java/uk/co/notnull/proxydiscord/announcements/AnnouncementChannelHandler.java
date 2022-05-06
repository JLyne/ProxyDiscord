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
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.listener.message.MessageDeleteListener;
import org.javacord.api.listener.message.MessageEditListener;
import org.javacord.api.util.event.ListenerManager;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.Util;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AnnouncementChannelHandler {
	private final ProxyDiscord plugin;
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

	public AnnouncementChannelHandler(String channelId) {
		this(channelId, Collections.emptyList());
	}

	public AnnouncementChannelHandler(String channelId, List<String> serverList) {
		this.channelId = channelId;

		this.plugin = ProxyDiscord.inst();
		this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();
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

		proxy.getEventManager().register(plugin, this);

		createListeners();
		getLatestMessage();
	}

	private TextChannel getChannel() {
		Optional<ServerTextChannel> announcementChannel = plugin.getDiscord().getApi()
                    .getServerTextChannelById(channelId);

		if(announcementChannel.isEmpty()) {
			throw new RuntimeException("Channel " + channelId + " not found");
		}

		channelName = "#" + announcementChannel.get().getName();

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
				plugin.getDebugLogger().info("Retrieved latest announcement for " + channelName);
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
        String content = message.getReadableContent();
        String headingKey = isNew ? "announcement-new" : "announcement-latest";
        String text = content.length() > 250 ? content.subSequence(0, 250) + "..." : content;

		TextComponent.Builder announcement = Component.text()
				.append(Messages.getComponent(headingKey,
											  Collections.singletonMap("channel", channelName),
											  Collections.emptyMap()))
				.append(Component.newline())
				.append(Util.markdownSerializer.serialize(text));

		if(content.length() > 250) {
            announcement.append(Component.newline()).append(Messages.getComponent("announcement-read-more"));
        }

		Component finalMessage = announcement.build();

        if(player != null) {
			sentLatestMessage.add(player.getUniqueId());
            player.sendMessage(Identity.nil(), finalMessage);
        } else if(servers != null) {
        	sentLatestMessage.clear();

        	List<Player> recipients = proxy.getAllPlayers().stream()
					.filter(p -> p.getCurrentServer().isPresent()
							&& servers.contains(p.getCurrentServer().get().getServer()))
					.collect(Collectors.toList());

            recipients.forEach(p ->{
            	sentLatestMessage.add(p.getUniqueId());
            	p.sendMessage(Identity.nil(), finalMessage);
			});
        } else {
        	sentLatestMessage.clear();
        	RegisteredServer linkingServer = plugin.getVerificationManager().getLinkingServer();

        	List<Player> recipients = proxy.getAllPlayers().stream()
					.filter(p -> p.getCurrentServer().isPresent()
							&& (linkingServer == null || !p.getCurrentServer().get().getServer().equals(linkingServer)))
					.collect(Collectors.toList());

        	recipients.forEach(p -> {
            	sentLatestMessage.add(p.getUniqueId());
        		p.sendMessage(Identity.nil(), finalMessage);
			});
		}
    }

    public void remove() {
		createListener.remove();
		editListener.remove();
		deleteListener.remove();
		proxy.getEventManager().unregisterListener(plugin, this);
	}

	@Subscribe(order = PostOrder.LATE)
	public void onPlayerConnected(ServerPostConnectEvent event) {
		Player player = event.getPlayer();

		if(player.getCurrentServer().isEmpty()) {
			return;
		}

		RegisteredServer server = player.getCurrentServer().get().getServer();

		if(plugin.getVerificationManager().getLinkingServer().equals(server)) {
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
