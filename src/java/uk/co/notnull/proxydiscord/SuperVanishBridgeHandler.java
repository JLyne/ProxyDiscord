/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2022 James Lyne
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

package uk.co.notnull.proxydiscord;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import uk.co.notnull.proxydiscord.api.events.DiscordLogEvent;
import uk.co.notnull.proxydiscord.api.events.PlayerInfoEvent;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;
import uk.co.notnull.proxydiscord.api.logging.LogType;
import uk.co.notnull.supervanishbridge.api.SuperVanishBridgeAPI;

public class SuperVanishBridgeHandler {
	private final SuperVanishBridgeAPI superVanishBridgeAPI;

	private final ProxyDiscord plugin;

	public SuperVanishBridgeHandler(ProxyDiscord plugin) {
		this.plugin = plugin;
		superVanishBridgeAPI = (SuperVanishBridgeAPI) plugin.getProxy().getPluginManager()
				.getPlugin("supervanishbridge").get().getInstance().get();
		plugin.getProxy().getEventManager().register(plugin, this);
	}

	@Subscribe
	public void onPlayerInfo(PlayerInfoEvent event) {
		if(superVanishBridgeAPI.isVanished(event.getPlayerInfo().getUuid())) {
			event.getPlayerInfo().setVanished(true);
		}
	}

	@Subscribe
	public void onPlayerInfo(DiscordLogEvent event) {
		LogEntry entry = event.getLogEntry();

		if(entry.getType() != LogType.JOIN && entry.getType() != LogType.LEAVE) {
			return;
		}

		if(superVanishBridgeAPI.isVanished(entry.getPlayer())) {
			event.setResult(DiscordLogEvent.DiscordLogResult.privateOnly());
		}
	}

	public boolean canSee(CommandSource source, Player player) {
		return !(source instanceof Player) || superVanishBridgeAPI.canSee((Player) source, player);
	}

	public boolean isVanished(Player player) {
		return superVanishBridgeAPI.isVanished(player);
	}

	//TODO: Adjust serverinfo player count
}
