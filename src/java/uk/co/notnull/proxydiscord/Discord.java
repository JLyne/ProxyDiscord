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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.interaction.*;
import org.slf4j.Logger;

public class Discord {
	/**
	 * Discord API Instance
	 */
	private DiscordApi api;

	private boolean connected = false;

	private final Logger logger;

	/**
	 * Class
	 * @param config configuration
	 */
	public Discord(ProxyDiscord plugin, ConfigurationNode config) {
		this.logger = plugin.getLogger();

		String token = config.getNode("bot", "token").getString(null);

		if(token == null || token.isEmpty()) {
			throw new IllegalArgumentException("No bot token provided, check the config");
		}

		try {
			logger.info("Connecting to Discord...");
			api = new DiscordApiBuilder().setToken(token)
					.setIntents(Intent.GUILDS, Intent.GUILD_EMOJIS, Intent.GUILD_MEMBERS,
								Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT)
					.setWaitForUsersOnStartup(true)
					.setWaitForServersOnStartup(true)
					.setShutdownHookRegistrationEnabled(false)
					.login().join();

			connected = true;
		} catch (CompletionException e) {
			logger.error("Failed to connect to Discord. Did you put a valid token in the config?");
			e.printStackTrace();
			return;
		}

        logger.info("Bot Invite Link: " + api.createBotInvite());

		//Don't cache any messages by default
		api.setMessageCacheSize(0, 0);

		updateActivity(config);
		createSlashCommands(false).join();

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
		ActivityType type = switch (activityType.toLowerCase()) {
			case "streaming" -> ActivityType.STREAMING;
			case "listening" -> ActivityType.LISTENING;
			case "watching" -> ActivityType.WATCHING;
			case "competing" -> ActivityType.COMPETING;
			default -> ActivityType.PLAYING;
		};

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

	public CompletableFuture<Void> disconnect() {
		if(api != null) {
			return api.disconnect();
		} else {
			return CompletableFuture.completedFuture(null);
		}
	}

	public CompletableFuture<Void> createSlashCommands(boolean clean) {
		if(clean) {
			return api.getGlobalSlashCommands().thenCompose(commands -> {
				List<CompletableFuture<Void>> futures = commands.stream()
						.map(ApplicationCommand::delete).toList();

				return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).exceptionally(e -> {
					if(e != null) {
						logger.warn("An error occurred while removing slash command. Changes may not be fully applied.");
						e.printStackTrace();
					}

					return null;
				}).thenCompose((unused) -> createSlashCommands(false));
			});
		}

		Set<ApplicationCommandBuilder<?, ?, ?>> commands = new HashSet<>();

		commands.add(
				SlashCommand.with("link", Messages.get("slash-command-link-description"))
						.addOption(SlashCommandOption.create(
								SlashCommandOptionType.STRING, "token",
								Messages.get("slash-command-link-token-argument-description"),
								true))
						.setDefaultEnabledForEveryone());

		commands.add(
				SlashCommand.with("unlink", Messages.get("slash-command-unlink-description"))
						.setDefaultDisabled());

		List<SlashCommandOption> infoSubcommands = new ArrayList<>();

		infoSubcommands.add(SlashCommandOption.createWithOptions(
				SlashCommandOptionType.SUB_COMMAND, "player",
				Messages.get("slash-command-info-player-description"),
				Collections.singletonList(SlashCommandOption.createStringOption(
						"username_or_uuid",
						Messages.get("slash-command-info-username-argument-description"),
						true, true))));

//		infoSubcommands.add(SlashCommandOption.createWithOptions(
//				SlashCommandOptionType.SUB_COMMAND, "server", "Returns information for a server",
//				Collections.singletonList(
//						SlashCommandOption.createStringOption("servername", "The server name",
//															  true, true))));

		infoSubcommands.add(SlashCommandOption.createWithOptions(
				SlashCommandOptionType.SUB_COMMAND, "discord",
				Messages.get("slash-command-info-discord-description"),
				Collections.singletonList(
						SlashCommandOption.create(SlashCommandOptionType.USER, "user",
												  Messages.get("slash-command-info-user-argument-description"),
												  true))));

        commands.add(
				SlashCommand.with("info", Messages.get("slash-command-info-description"), infoSubcommands)
						.setDefaultEnabledForPermissions(PermissionType.MANAGE_ROLES));

		commands.add(
				UserContextMenu
						.with(Messages.get("context-menu-info-label"))
						.setDefaultEnabledForPermissions(PermissionType.MANAGE_ROLES));

		return api.bulkOverwriteGlobalApplicationCommands(commands).thenAccept((result) -> {}).exceptionally(e -> {
			logger.warn("An error occurred while registering slash commands. Commands may not function correctly.");
			e.printStackTrace();
			return null;
		});
	}

	public void reload(ConfigurationNode config) {
		if(connected && !api.getToken().equals(config.getNode("bot", "token").getString(null))) {
			logger.warn("You must restart the proxy for bot token changes to take effect");
		}

		if(connected) {
			updateActivity(config);
			createSlashCommands(false);
		}

		logger.warn("If you have made changes to application command messages, you may also need to run /discord refreshcommands, or restart the proxy");
	}
}