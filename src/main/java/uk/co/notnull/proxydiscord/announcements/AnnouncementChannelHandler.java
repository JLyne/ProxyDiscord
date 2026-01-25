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

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.Util;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class AnnouncementChannelHandler extends ListenerAdapter {
	private final ProxyDiscord plugin;
	private final ProxyServer proxy;
	private final Logger logger;

	private final StandardGuildMessageChannel channel;
	private final Set<RegisteredServer> servers;

	private final String channelName;
	private final TextComponent channelComponent;

	private final Set<UUID> sentLatestMessage;

	private Message lastMessage;

	public AnnouncementChannelHandler(@NotNull StandardGuildMessageChannel channel) {
		this(channel, Collections.emptyList());
	}

	public AnnouncementChannelHandler(@NotNull StandardGuildMessageChannel channel, List<String> serverList) {
		Objects.requireNonNull(channel);
		this.channel = channel;

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
					logger.warn("Unknown server {} for announcement channel {} in {}", name, channel.getId(),
								channel.getGuild().getName());
				}
			});

			this.servers = servers;
		}

		channelName = "#" + channel.getName();
		channelComponent = Component.text(channelName).clickEvent(ClickEvent.openUrl(channel.getJumpUrl()));

		logger.info("Announcements enabled for channel: {} (id: {}) in {}", channelName, channel.getId(),
					channel.getGuild().getName());

		getLatestMessage();
	}

	public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
		if (event.getChannel().equals(channel)) {
			sendAnnouncement(event.getMessage());
		}
	}

	public void onMessageUpdate(@Nonnull MessageUpdateEvent event) {
		if(event.getMessage().equals(lastMessage)) {
			lastMessage = event.getMessage();
		}
	}

	public void onMessageDelete(@Nonnull MessageDeleteEvent event) {
		if(lastMessage == null || event.getMessageIdLong() == lastMessage.getIdLong()) {
			getLatestMessage();
		}
	}

	private void getLatestMessage() {
		lastMessage = null;

		channel.getHistory().retrievePast(1).submit().thenAcceptAsync(messages -> {
			if(messages.getFirst() != null) {
				plugin.getDebugLogger().info("Retrieved latest announcement for " + channelName);
				lastMessage = messages.getFirst();
			}
		}).exceptionally(e -> {
			logger.warn("Failed to retrieve latest announcement for {} in {}", channelName, channel.getGuild().getName());
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
        String content = message.getContentDisplay();
        String headingKey = isNew ? "announcement-new" : "announcement-latest";

		var state = new Object() {
			int length = 0;
			boolean truncated = false;
		};

        String text = Arrays.stream(content.split(" ")).takeWhile(w -> {
			if(state.length > 250) {
				state.truncated = true;
				return false;
			}

			state.length += w.length();
			return true;
		}).collect(Collectors.joining(" "));

		TextComponent.Builder announcement = Component.text()
				.append(Messages.getComponent(headingKey,
											  Collections.emptyMap(),
											  Collections.singletonMap("channel", channelComponent)))
				.append(Component.newline());

		if(state.truncated) {
			announcement.append(Util.prepareDiscordMessage(text + "..."));
			announcement.append(Util.prepareDiscordMessageAttachments(message));
			announcement.append(Component.newline()).append(
					Messages.getComponent("announcement-read-more")
							.clickEvent(ClickEvent.openUrl(message.getJumpUrl()))
							.hoverEvent(HoverEvent.showText(Messages.getComponent("announcement-read-more-tooltip"))));
        } else {
			announcement.append(Util.prepareDiscordMessage(text));
			announcement.append(Util.prepareDiscordMessageAttachments(message));
		}

		Component finalMessage = announcement.build();

        if(player != null) {
			sentLatestMessage.add(player.getUniqueId());
            player.sendMessage(finalMessage);
        } else if(servers != null) {
        	sentLatestMessage.clear();

        	List<Player> recipients = proxy.getAllPlayers().stream()
					.filter(p -> p.getCurrentServer().isPresent()
							&& servers.contains(p.getCurrentServer().get().getServer()))
					.toList();

            recipients.forEach(p ->{
            	sentLatestMessage.add(p.getUniqueId());
            	p.sendMessage(finalMessage);
			});
        } else {
        	sentLatestMessage.clear();
        	RegisteredServer linkingServer = plugin.getVerificationManager().getLinkingServer();

        	List<Player> recipients = proxy.getAllPlayers().stream()
					.filter(p -> p.getCurrentServer().isPresent()
							&& (linkingServer == null || !p.getCurrentServer().get().getServer().equals(linkingServer)))
					.toList();

        	recipients.forEach(p -> {
            	sentLatestMessage.add(p.getUniqueId());
        		p.sendMessage(finalMessage);
			});
		}
    }

	@Subscribe(priority = Short.MIN_VALUE / 2)
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

	@Subscribe(priority = Short.MIN_VALUE / 2)
	public void onPlayerDisconnected(DisconnectEvent event) {
		sentLatestMessage.remove(event.getPlayer().getUniqueId());
	}
}
