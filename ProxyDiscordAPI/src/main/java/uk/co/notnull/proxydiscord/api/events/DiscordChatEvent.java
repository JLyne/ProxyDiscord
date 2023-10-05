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

package uk.co.notnull.proxydiscord.api.events;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.luckperms.api.model.user.User;

import java.util.Objects;
import java.util.Set;

/**
 * This event is fired when ProxyDiscord receives a message from a Discord logging channel
 */
@SuppressWarnings("unused")
public final class DiscordChatEvent implements ResultedEvent<PlayerChatEvent.ChatResult> {
	private final User sender;
	private final Set<RegisteredServer> servers;
	private final String messageContent;
	private PlayerChatEvent.ChatResult result = PlayerChatEvent.ChatResult.allowed();

	/**
	 * Constructs a DiscordChatEvent.
	 * @param sender The Luckperms user of the Minecraft account which is linked to the sender Discord account
	 * @param messageContent The sent message content
	 * @param servers The servers the message is to be shown in
	 */
	public DiscordChatEvent(User sender, String messageContent, Set<RegisteredServer> servers) {
		this.sender = sender;
		this.messageContent = messageContent;
		this.servers = servers;
	}

	/**
	 * Gets the luckperms {@link User} of the sending Discord account
	 * @return the user
	 * @deprecated use getSender instead
	 */
	@Deprecated
	public User getUser() {
		return sender;
	}

	/**
	 * Gets the luckperms {@link User} of the sending Discord account
	 * @return the user
	 */
	public User getSender() {
		return sender;
	}

	/**
	 * Gets the sent message
	 * @return the message
	 * @deprecated Use getMessageContent instead
	 */
	@Deprecated(forRemoval = true)
	public String getMessage() {
		return messageContent;
	}

	/**
	 * Gets the sent message
	 * @return the message
	 */
	public String getMessageContent() {
		return messageContent;
	}

	public PlayerChatEvent.ChatResult getResult() {
		return result;
	}

	public void setResult(PlayerChatEvent.ChatResult result) {
		this.result = Objects.requireNonNull(result);
	}

	/**
	 * Gets the servers which the message will be shown in
	 * @return the servers
	 */
	public Set<RegisteredServer> getServers() {
		return servers;
	}

	@Override
	public String toString() {
		return "PlayerChatEvent{"
				+ "sender=" + sender
				+ ", messageContent=" + messageContent
				+ ", result=" + result
				+ '}';
	}
}
