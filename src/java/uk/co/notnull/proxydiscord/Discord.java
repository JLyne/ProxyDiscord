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

package uk.co.notnull.proxydiscord;

import ninja.leaping.configurate.ConfigurationNode;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.DiscordApiBuilder;

import java.util.concurrent.CompletionException;

import org.javacord.api.DiscordApi;
import org.javacord.api.interaction.*;
import org.slf4j.Logger;

public class Discord {
	/**
	 * Discord API Instance
	 */
	private DiscordApi api;

	private boolean connected = false;

	private final Logger logger;

	private final boolean slashCommandsAllowed;

	/**
	 * Class
	 * @param config configuration
	 */
	public Discord(ProxyDiscord plugin, ConfigurationNode config) {
		this.logger = plugin.getLogger();

		slashCommandsAllowed = config.getNode("bot", "slash-commands").getBoolean(false);

		String token = config.getNode("bot", "token").getString(null);

		if(token == null || token.isEmpty()) {
			throw new IllegalArgumentException("No bot token provided, check the config");
		}

		try {
			logger.info("Connecting to Discord...");
			api = new DiscordApiBuilder().setToken(token)
					.setIntents(Intent.GUILDS, Intent.GUILD_EMOJIS, Intent.GUILD_MEMBERS, Intent.GUILD_MESSAGES)
					.setWaitForUsersOnStartup(true)
					.setWaitForServersOnStartup(true)
					.login().join();

			if(!slashCommandsAllowed) {
				api.getGlobalSlashCommands().join().forEach(slashCommand -> slashCommand.deleteGlobal().join());
			}

			connected = true;
		} catch (CompletionException e) {
			logger.error("Failed to connect to Discord. Did you put a valid token in the config?");
			e.printStackTrace();
			return;
		}

        logger.info("Bot Invite Link: " + api.createBotInvite());

		//Dont cache any messages by default
		api.setMessageCacheSize(0, 0);

		updateActivity(config);

		//Handle disconnects/reconnects
        api.addLostConnectionListener(event -> {
			connected = false;
			logger.warn("Lost connection to Discord");
		});

        api.addReconnectListener(event -> {
			connected = true;
			logger.info("Reconnected to Discord");
			updateActivity(config);
        });

		api.addResumeListener(event -> {
			connected = true;
			logger.info("Resumed connection to Discord");
			updateActivity(config);
		});
	}

	private void updateActivity(ConfigurationNode config) {
		String activity = config.getNode("bot", "activity").getString(null);
		String activityType = config.getNode("bot", "activity-type").getString("");
		ActivityType type = ActivityType.PLAYING;

		switch (activityType.toLowerCase()) {
			case "streaming":
				type = ActivityType.STREAMING;
				break;

			case "listening":
				type = ActivityType.LISTENING;
				break;

			case "watching":
				type = ActivityType.WATCHING;
				break;

			case "competing":
				type = ActivityType.COMPETING;
				break;
		}

		if(activity != null && !activity.isEmpty()) {
        	api.updateActivity(type, activity);
		}
	}

	public DiscordApi getApi() {
		return api;
	}

	public Boolean isConnected() {
		return connected;
	}

	public SlashCommand createSlashCommand(SlashCommandBuilder slashCommand) {
		if(!slashCommandsAllowed) {
			return null;
		}

		return slashCommand.createGlobal(api).join();
	}
}