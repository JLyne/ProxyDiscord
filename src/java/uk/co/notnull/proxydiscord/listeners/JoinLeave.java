/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2021 James Lyne
 *
 * Some portions of this file were taken from https://github.com/Prouser123/BungeeDiscord
 * These portions are Copyright (c) 2018 James Cahill
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

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.manager.GroupSyncManager;
import uk.co.notnull.proxydiscord.manager.LoggingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import uk.co.notnull.proxydiscord.api.VerificationResult;

public class JoinLeave {
	private final ProxyDiscord plugin;
	private final Logger logger;

	private static VerificationManager verificationManager;
	private static GroupSyncManager groupSyncManager;
	private static LoggingManager loggingManager;

	public JoinLeave(ProxyDiscord plugin) {
    	this.plugin = plugin;
        this.logger = plugin.getLogger();

        verificationManager = plugin.getVerificationManager();
        groupSyncManager = plugin.getGroupSyncManager();
		loggingManager = plugin.getLoggingManager();
    }

	@Subscribe(order = PostOrder.FIRST)
	public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
		Player player = event.getPlayer();

		player.sendMessage(Identity.nil(), Component.text(Messages.get("join-welcome"))
			.color(NamedTextColor.GREEN));

		if(!plugin.getDiscord().isConnected()) {
			player.sendMessage(Identity.nil(), Component.text(
					Messages.get("discord-issues")).color(NamedTextColor.RED));
		}

		groupSyncManager.syncPlayer(player).thenRun(() -> {
			VerificationResult result = verificationManager.checkVerificationStatus(player);
			logger.info("Player " + player.getUsername() + " joined with verification status " + result);
		});
	}
	
	@Subscribe(order = PostOrder.LAST)
	public void onDisconnect(DisconnectEvent event) {
		Player player = event.getPlayer();

		verificationManager.clearPlayerStatus(player);
	}
}