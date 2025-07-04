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

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
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

	@Subscribe(priority = Short.MAX_VALUE - 1)
	public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
		Player player = event.getPlayer();

		Messages.sendComponent(player, "join-welcome");

		if(!plugin.getDiscord().isConnected()) {
			Messages.sendComponent(player, "discord-issues");
		}

		groupSyncManager.syncPlayer(player).thenRun(() -> {
			VerificationResult result = verificationManager.checkVerificationStatus(player);
			logger.info("Player " + player.getUsername() + " joined with verification status " + result);
		});
	}
	
	@Subscribe(priority = Short.MIN_VALUE + 1)
	public void onDisconnect(DisconnectEvent event) {
		Player player = event.getPlayer();

		verificationManager.clearPlayerStatus(player);
	}
}