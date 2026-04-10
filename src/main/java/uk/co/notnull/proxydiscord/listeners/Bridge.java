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

package uk.co.notnull.proxydiscord.listeners;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.BridgeManager;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Bridge {
	private static final ChannelIdentifier channel = MinecraftChannelIdentifier.create("proxydiscord", "event");
	private static final TypeToken<Map<String, String>> mapType = new TypeToken<>() {};
	private final BridgeManager bridgeManager;
	private final Gson gson = new Gson();
	private final ProxyDiscord plugin;

	public Bridge(ProxyDiscord plugin) {
		this.plugin = plugin;
		bridgeManager = plugin.getBridgeManager();
		plugin.getProxy().getChannelRegistrar().register(channel);
	}
	
	@Subscribe
	public void onPluginMessage(PluginMessageEvent e) {
		plugin.getLogger().info("PluginMessageEvent");
		if (!e.getIdentifier().equals(channel)) {
			plugin.getLogger().info("Wrong channel {}", e.getIdentifier());
			return;
		}

		e.setResult(PluginMessageEvent.ForwardResult.handled());

		if (!(e.getSource() instanceof ServerConnection connection)) {
			plugin.getLogger().info("Not from server?");
			return;
		}

		Map<String, String> data = gson.fromJson(new String(e.getData(), StandardCharsets.UTF_8), mapType.getType());
		plugin.getLogger().info("Do thing");
		bridgeManager.handleEvent(connection, data);
	}
}
