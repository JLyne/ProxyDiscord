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

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;

import uk.co.notnull.proxydiscord.bot.commands.Info;
import uk.co.notnull.proxydiscord.bot.commands.Link;
import uk.co.notnull.proxydiscord.bot.commands.Unlink;
import uk.co.notnull.proxydiscord.bot.listeners.MemberListener;
import uk.co.notnull.proxydiscord.bot.listeners.RoleListener;
import uk.co.notnull.proxydiscord.bot.listeners.SessionListener;

public final class Discord implements EventListener {
	/**
	 * Discord API Instance
	 */
	private JDA jda;

	private boolean connected = false;

	private final Logger logger;

	/**
	 * Class
	 * @param config configuration
	 */
	public Discord(ProxyDiscord plugin, ConfigurationNode config) {
		this.logger = plugin.getLogger();

		String token = config.node("bot", "token").getString();

		if(token == null || token.isEmpty()) {
			throw new IllegalArgumentException("No bot token provided, check the config");
		}

		try {
			MessageRequest.setDefaultMentions(Collections.emptyList());
											  logger.info("Connecting to Discord...");
			jda = JDABuilder.createLight(token,
										 GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_EXPRESSIONS,
										 GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
					.enableCache(CacheFlag.EMOJI)
					.setMemberCachePolicy(MemberCachePolicy.ALL)
					.setChunkingFilter(ChunkingFilter.ALL)
					.addEventListeners(this,
							new RoleListener(plugin),
							new MemberListener(plugin),
							new SessionListener(plugin),
							new Link(plugin),
							new Unlink(plugin),
							new Info(plugin)
					)
					.setActivity(getActivity(config))
					.build();
		} catch (InvalidTokenException e) {
			logger.error("Failed to connect to discord. Bot token is invalid.");
		} catch(IllegalArgumentException e) {
			logger.error("Failed to connect to discord.", e);
		}
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		switch (event) {
			case ReadyEvent _ -> {
				connected = true;
				logger.info("Connected to Discord");
				logger.info("Bot Invite Link: {}", jda.getInviteUrl());
				logger.info("Run /discord refreshcommands to register or update interactions");
			}
			case SessionDisconnectEvent _ -> {
				connected = false;
				logger.warn("Lost connection to Discord");
			}
			case SessionResumeEvent _ -> {
				connected = true;
				logger.info("Resumed connection to Discord");
			}
			case SessionRecreateEvent _ -> {
				connected = true;
				logger.info("Reconnected to Discord");
			}
			default -> {}
		}
	}

	private Activity getActivity(ConfigurationNode config) {
		String activityName = config.node("bot", "activity").getString("");
		String activityType = config.node("bot", "activity-type").getString();
		String activityUrl = config.node("bot", "activity-url").getString();

		if(activityType == null) {
			return null;
		}

		Activity.ActivityType type = switch (activityType.toLowerCase()) {
			case "streaming" -> Activity.ActivityType.STREAMING;
			case "listening" -> Activity.ActivityType.LISTENING;
			case "watching" -> Activity.ActivityType.WATCHING;
			case "competing" -> Activity.ActivityType.COMPETING;
			case "custom" -> Activity.ActivityType.CUSTOM_STATUS;
			default -> Activity.ActivityType.PLAYING;
		};

		return Activity.of(type, activityName, activityUrl);
	}

	public JDA getJDA() {
		return jda;
	}

	public Boolean isConnected() {
		return connected;
	}

	public void disconnect() {
		if(jda != null) {
			jda.shutdown();
		}
	}

	public CompletableFuture<Void> createSlashCommands() {
		CompletableFuture<Void> future = new CompletableFuture<>();

		try {
			jda.updateCommands().addCommands(
							Commands.slash("link", Messages.get("slash-command-link-description"))
									.addOption(OptionType.STRING, "token",
											   Messages.get("slash-command-link-token-argument-description"), true)
									.setDefaultPermissions(DefaultMemberPermissions.ENABLED),
							Commands.slash("unlink", Messages.get("slash-command-unlink-description"))
									.setDefaultPermissions(DefaultMemberPermissions.ENABLED),
							Commands.slash("info", Messages.get("slash-command-info-description"))
									.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
									.addSubcommands(
											new SubcommandData("player", Messages.get("slash-command-info-player-description"))
													.addOption(OptionType.STRING, "username_or_uuid",
															   Messages.get("slash-command-info-username-argument-description"),
															   true, true),
											new SubcommandData("discord", Messages.get("slash-command-info-discord-description"))
													.addOption(OptionType.USER, "user",
															   Messages.get("slash-command-info-user-argument-description"),
															   true)),
							Commands.context(Command.Type.USER, Messages.get("context-menu-info-label")))
					.queue(_ -> {
						logger.info("Interactions registered.");
						future.complete(null);
					},
					(e) -> {
						logger.warn("An error occurred while registering interactions. Commands may not function correctly.", e);
						future.completeExceptionally(e);
					});
		} catch (RejectedExecutionException e) {
			future.completeExceptionally(e);
		}

		return future;
	}

	public void reload(ConfigurationNode config) {
		if(!jda.getToken().equals(config.node("bot", "token").getString())) {
			logger.warn("You must restart the proxy for bot token changes to take effect");
		}

		if(connected) {
			jda.getPresence().setActivity(getActivity(config));
		}

		logger.warn("If you have made changes to application command messages, you will also need to run /discord refreshcommands");
	}
}