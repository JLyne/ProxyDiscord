/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2026 James Lyne
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

package uk.co.notnull.proxydiscord.manager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;
import uk.co.notnull.proxydiscord.api.logging.LogType;
import uk.co.notnull.proxydiscord.api.logging.LogVisibility;

import java.util.Map;

public class BridgeManager {
	private final ProxyDiscord plugin;
	private final Logger logger;

	public BridgeManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

	public void handleEvent(ServerConnection connection, Map<String, String> data) {
		Player player = connection.getPlayer();
		RegisteredServer server = connection.getServer();
		LogEntry.Builder entry = LogEntry.builder();
		boolean privateLog = plugin.getVanishBridgeHelper().isVanished(player);

		entry.player(player)
				.server(server)
				.visibility(privateLog ? LogVisibility.PRIVATE_ONLY : LogVisibility.UNSPECIFIED);

		switch (data.get("type")) {
			case "advancement" -> {
				entry.type(LogType.ADVANCEMENT);
				entry.replacements(Map.of(
						"advancement_title", data.get("advancement_title"),
						"advancement_description", data.get("advancement_description"),
						"advancement_message", data.get("advancement_message")
				));
			}
			case "death" -> {
				entry.type(LogType.DEATH);
				entry.replacements(Map.of("death_message", data.get("death_message")));
			}
			case null, default -> plugin.getLogger().warn("Ignoring invalid event type {}", data.get("type"));
		}

		plugin.getLoggingManager().logEvent(entry.build());
	}
}
