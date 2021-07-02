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

import com.velocitypowered.api.proxy.Player;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unused")
public final class PlayerUnlinkEvent {
	private final UUID uuid;
	private final Player player;
	private final long discordId;

	public PlayerUnlinkEvent(UUID uuid, long discordId) {
		this.uuid = uuid;
		this.player = null;
		this.discordId = discordId;
	}

	public PlayerUnlinkEvent(Player player, long discordId) {
		this.uuid = player.getUniqueId();
		this.player = player;
		this.discordId = discordId;
	}

	public UUID getUuid() {
		return uuid;
	}

	public Optional<Player> getPlayer() {
		return Optional.ofNullable(player);
	}

	public long getDiscordId() {
		return discordId;
	}
}
