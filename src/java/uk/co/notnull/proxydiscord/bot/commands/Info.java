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

package uk.co.notnull.proxydiscord.bot.commands;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.luckperms.api.model.user.UserManager;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.ApplicationCommandEvent;
import org.javacord.api.event.interaction.AutocompleteCreateEvent;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.event.interaction.UserContextMenuCommandEvent;
import org.javacord.api.interaction.AutocompleteInteraction;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.javacord.api.interaction.SlashCommandOptionChoice;
import org.javacord.api.interaction.callback.InteractionImmediateResponseBuilder;
import org.javacord.api.interaction.callback.InteractionOriginalResponseUpdater;
import org.javacord.api.listener.interaction.AutocompleteCreateListener;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;
import org.javacord.api.listener.interaction.UserContextMenuCommandListener;
import org.jetbrains.annotations.NotNull;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.Util;
import uk.co.notnull.proxydiscord.api.VerificationResult;
import uk.co.notnull.proxydiscord.api.events.PlayerInfoEvent;
import uk.co.notnull.proxydiscord.api.info.PlayerInfo;
import uk.co.notnull.proxydiscord.manager.LinkingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Info implements SlashCommandCreateListener, UserContextMenuCommandListener {
	private final ProxyDiscord plugin;
	private final UserManager userManager;
	private final LinkingManager linkingManager;
	private final VerificationManager verificationManager;

    public Info(ProxyDiscord plugin) {
		this.plugin = plugin;
	    this.userManager = ProxyDiscord.inst().getLuckpermsManager().getUserManager();
	    this.linkingManager = ProxyDiscord.inst().getLinkingManager();
	    this.verificationManager = ProxyDiscord.inst().getVerificationManager();
	}

    public void onSlashCommandCreate(SlashCommandCreateEvent event) {
        SlashCommandInteraction interaction = event.getSlashCommandInteraction();

		if(!interaction.getCommandName().equals("info")) {
			return;
		}

		String subcommand = interaction.getOptions().get(0).getName();

		switch (subcommand) {
			case "discord" -> //noinspection OptionalGetWithoutIsPresent
					handleDiscordInfoRequest(interaction.getArguments().get(0).getUserValue().get(), event);

			case "player" -> //noinspection OptionalGetWithoutIsPresent
					handleMinecraftInfoRequest(interaction.getArguments().get(0).getStringValue().get(), event);

			case "server" -> event.getInteraction()
					.createImmediateResponder()
					.setContent("TODO")
					.respond();
		}
    }

	public void onUserContextMenuCommand(UserContextMenuCommandEvent event) {
		handleDiscordInfoRequest(event.getUserContextMenuInteraction().getTarget(), event);
	}

	private void handleDiscordInfoRequest(User user, ApplicationCommandEvent event) {
		UUID uuid = linkingManager.getLinked(user.getId());

		if(uuid == null) {
			respondWithEmbed(event, Messages.getEmbed(
					"embed-info-discord-not-linked",
					Collections.singletonMap("discord", user.getMentionTag())));
		} else {
			respondWithPlayerInfo(event, uuid);
		}
	}

	/**
	 * Handles a command invocation requesting information for the given Minecraft username or UUID
	 * @param usernameOrUUID - UUID or username to return information for
	 * @param event - The command event to respond to
	 */
	private void handleMinecraftInfoRequest(String usernameOrUUID, ApplicationCommandEvent event) {
		if(Util.isValidUUID(usernameOrUUID)) {
			respondWithPlayerInfo(event, UUID.fromString(usernameOrUUID));
		} else {
			userManager.lookupUniqueId(usernameOrUUID).thenAccept(uuid -> {
				if(uuid == null) {
					respondWithEmbed(event, Messages.getEmbed("embed-info-player-not-found"));
				} else {
					respondWithPlayerInfo(event, uuid);
				}
			});
		}
	}

	/**
	 * Sends a response to the given command event containing the given embed
	 * @param event - The command event to respond to
	 * @param embed - The embed to respond with
	 */
	private void respondWithEmbed(@NotNull ApplicationCommandEvent event, EmbedBuilder embed) {
		InteractionImmediateResponseBuilder builder = event.getInteraction().createImmediateResponder();

		builder.addEmbed(embed)
				.respond()
				.exceptionally(e -> {
					plugin.getLogger().warn("Failed to immediately respond to interaction");
					e.printStackTrace();
					builder.removeAllEmbeds().addEmbed(Messages.getEmbed("embed-info-error")).respond();
					return null;
				});
	}

	/**
	 * Sends a response to the given command event containing player info for the given UUID.
	 * If the given UUID is not known to the server, a "Player not found" response will be sent.
	 * @param event - The command event to respond to
	 * @param uuid - The UUID of the user to return information for
	 */
	private void respondWithPlayerInfo(@NotNull ApplicationCommandEvent event, @NotNull UUID uuid) {
		CompletableFuture<InteractionOriginalResponseUpdater> updaterFuture = event.getInteraction().respondLater();
		CompletableFuture<net.luckperms.api.model.user.User> userFuture = userManager.loadUser(uuid);
		CompletableFuture<User> discordFuture;
		Long discordId = linkingManager.getLinked(uuid);

		if (discordId != null) {
			discordFuture = plugin.getDiscord().getApi().getUserById(discordId);
		} else {
			discordFuture = CompletableFuture.completedFuture(null);
		}

		CompletableFuture.allOf(updaterFuture, userFuture, discordFuture).thenCompose((unused) -> {
			net.luckperms.api.model.user.User user = userFuture.join();

			if (user.getUsername() == null) {
				return null;
			}

			PlayerInfo playerInfo = new PlayerInfo(uuid, user.getUsername());
			Optional<Player> onlinePlayer = plugin.getProxy().getPlayer(uuid);

			if (onlinePlayer.isPresent()) {
				playerInfo.setOnline(true);
				playerInfo.setCurrentServer(onlinePlayer.get().getCurrentServer()
													.map(ServerConnection::getServer)
													.orElse(null));
			}

			return plugin.getProxy().getEventManager().fire(new PlayerInfoEvent(playerInfo));
		}).thenApply(result -> {
			InteractionOriginalResponseUpdater updater = updaterFuture.join();

			if (result == null) {
				return updater.addEmbed(Messages.getEmbed("embed-info-player-not-found"));
			}

			return preparePlayerInfoResponse(updater, result.getPlayerInfo(), discordFuture.join());
		}).thenAccept(InteractionOriginalResponseUpdater::update).exceptionally(e -> {
			plugin.getLogger().warn("Failed to respond to interaction");
			e.printStackTrace();

			if (updaterFuture.isDone()) {
				updaterFuture.join().removeAllEmbeds().addEmbed(Messages.getEmbed("embed-info-error")).update();
			}

			return null;
		});
	}

	private InteractionOriginalResponseUpdater preparePlayerInfoResponse(
			InteractionOriginalResponseUpdater updater, PlayerInfo info, User discordUser) {
		String discord = discordUser != null ?
				Messages.get("info-discord-linked",
							 Collections.singletonMap("<discord>", discordUser.getMentionTag())) :
				Messages.get("info-discord-not-linked");

		String status = Messages.get("info-status-offline");
		VerificationResult verifyStatus = discordUser != null ?
				verificationManager.checkVerificationStatus(discordUser.getId()) :
				VerificationResult.NOT_LINKED;

		String access = switch (verifyStatus) {
			case UNKNOWN, NOT_LINKED -> Messages.get("info-access-not-linked");
			case LINKED_NOT_VERIFIED -> Messages.get("info-access-linked-missing-roles");
			case VERIFIED -> {
				String roles = verificationManager.getVerifiedRoles().stream()
						.filter(role -> role.hasUser(discordUser))
						.map(Role::getMentionTag).collect(Collectors.joining(", "));

				yield Messages.get("info-access-linked-roles", Collections.singletonMap("<roles>", roles));
			}
			case BYPASSED -> Messages.get("info-access-linked-bypassed");
			case NOT_REQUIRED -> Messages.get("info-access-not-configured");
		};

		if (info.isOnline() && !info.isVanished()) {
			if(info.getQueueInfo() != null) {
				status = Messages.get("info-status-queueing", Map.of(
						"<server>", info.getQueueInfo().server().getServerInfo().getName(),
						"<position>", String.valueOf(info.getQueueInfo().position())));
			} else if (info.getCurrentServer() != null) {
				status = Messages.get("info-status-online-server", Collections.singletonMap(
						"<server>", info.getCurrentServer().getServerInfo().getName()));
			} else {
				status = Messages.get("info-status-online");
			}
		}

		Map<String, String> replacements = Map.of(
				"minecraft", info.getUsername(),
				"uuid", info.getUuid().toString(),
				"online_status", status,
				"discord_status", discord,
				"access", access
		);

		return updater
				.addEmbed(Messages.getEmbed("embed-info", replacements))
				.addComponents(Messages.getMessageActionRow("info-actions", replacements).build());
	}
}