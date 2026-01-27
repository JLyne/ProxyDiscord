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
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.Util;
import uk.co.notnull.proxydiscord.api.VerificationResult;
import uk.co.notnull.proxydiscord.api.events.PlayerInfoEvent;
import uk.co.notnull.proxydiscord.api.info.PlayerInfo;
import uk.co.notnull.proxydiscord.manager.LinkingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.LuckPermsManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Info extends ListenerAdapter {
	private final ProxyDiscord plugin;
	private final LuckPermsManager luckPermsManager;
	private final LinkingManager linkingManager;
	private final VerificationManager verificationManager;

    public Info(ProxyDiscord plugin) {
		this.plugin = plugin;
	    this.luckPermsManager = ProxyDiscord.inst().getLuckpermsManager();
	    this.linkingManager = ProxyDiscord.inst().getLinkingManager();
	    this.verificationManager = ProxyDiscord.inst().getVerificationManager();
	}

    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SlashCommandInteraction interaction = event.getInteraction();

		if(!interaction.getName().equals("info")) {
			return;
		}

		String subcommand = interaction.getSubcommandName();

		switch (subcommand) {
			case "discord" ->
					handleDiscordInfoRequest(interaction.getOption("user").getAsUser(), event);

			case "player" ->
					handleMinecraftInfoRequest(interaction.getOption("username_or_uuid").getAsString(), event);
//
//			case "server" -> event.getInteraction()
//					.createImmediateResponder()
//					.setContent("TODO")
//					.respond();
		}
    }

	public void onUserContextInteraction(@NonNull UserContextInteractionEvent event) {
		handleDiscordInfoRequest(event.getInteraction().getTarget(), event);
	}

	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		CommandAutoCompleteInteraction interaction = event.getInteraction();

		if(!interaction.getName().equals("info")) {
			return;
		}

		String subcommand = interaction.getSubcommandName();

		switch (subcommand) {
			case "player" -> {
				String query = interaction.getFocusedOption().getValue().toLowerCase();
				List<Command.Choice> choices = ProxyDiscord.inst().getVanishBridgeHelper()
						.getPlayerSuggestions(query, null).stream()
						.limit(25)
						.map(player -> new Command.Choice(player.getUsername(),
															 player.getUniqueId().toString()))
						.toList();
				interaction.replyChoices(choices).queue();
			}
//
//			case "server" -> {
//				String query = interaction.getFocusedOption().getStringValue().orElse("").toLowerCase();
//				List<SlashCommandOptionChoice> choices = plugin.getProxy().getAllServers().stream()
//						.filter(server -> server.getServerInfo().getName().toLowerCase().startsWith(query))
//						.map(server -> SlashCommandOptionChoice.create(server.getServerInfo().getName(),
//																	   server.getServerInfo().getName()))
//						.toList();
//				interaction.respondWithChoices(choices);
//			}
		}
	}

	/**
	 * Handles a command invocation requesting information for the given Discord user
	 * @param user - The user to return information for
	 * @param event - The command event to respond to
	 */
	private void handleDiscordInfoRequest(User user, GenericCommandInteractionEvent event) {
		UUID uuid = linkingManager.getLinked(user.getIdLong());

		if(uuid == null) {
			respondWithComponent(event, Messages.getMessageComponents(
					"discord-info-not-linked",
					Collections.singletonMap("discord", user.getAsMention())));
		} else {
			respondWithPlayerInfo(event, uuid);
		}
	}

	/**
	 * Handles a command invocation requesting information for the given Minecraft username or UUID
	 * @param usernameOrUUID - UUID or username to return information for
	 * @param event - The command event to respond to
	 */
	private void handleMinecraftInfoRequest(String usernameOrUUID, GenericCommandInteractionEvent event) {
		if(Util.isValidUUID(usernameOrUUID)) {
			respondWithPlayerInfo(event, UUID.fromString(usernameOrUUID));
		} else {
			luckPermsManager.getUserManager().lookupUniqueId(usernameOrUUID).thenAccept(uuid -> {
				if(uuid == null) {
					respondWithComponent(event, Messages.getMessageComponents("discord-info-player-not-found"));
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
	private void respondWithComponent(@NotNull GenericCommandInteractionEvent event, @NotNull MessageComponentTree embed) {
		event.reply(
				new MessageCreateBuilder()
						.useComponentsV2()
						.mention(event.getUser())
						.addComponents(embed)
						.build())
				.queue(null, e -> {
					plugin.getLogger().warn("Failed to immediately respond to interaction", e);
					//builder.removeAllEmbeds().addEmbed(Messages.getMessageComponent()("discord-info-error")).respond(); //FIXME
			});
	}

	/**
	 * Sends a response to the given command event containing player info for the given UUID.
	 * If the given UUID is not known to the server, a "Player not found" response will be sent.
	 * @param event - The command event to respond to
	 * @param uuid - The UUID of the user to return information for
	 */
	private void respondWithPlayerInfo(@NotNull GenericCommandInteractionEvent event, @NotNull UUID uuid) {
		CompletableFuture<InteractionHook> updaterFuture = event.getInteraction().deferReply().submit();
		CompletableFuture<net.luckperms.api.model.user.User> userFuture = luckPermsManager.getUserManager().loadUser(uuid);
		CompletableFuture<net.dv8tion.jda.api.entities.User> discordFuture;
		Long discordId = linkingManager.getLinked(uuid);

		if (discordId != null) {
			discordFuture = event.getJDA().retrieveUserById(discordId).submit();
		} else {
			discordFuture = CompletableFuture.completedFuture(null);
		}

		CompletableFuture.allOf(updaterFuture, userFuture, discordFuture).thenCompose(_ -> {
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
				playerInfo.setVanished(plugin.getVanishBridgeHelper().isVanished(onlinePlayer.get()));
			}

			return plugin.getProxy().getEventManager().fire(new PlayerInfoEvent(playerInfo));
		}).thenApply(result -> {
			if (result == null) {
				return new MessageCreateBuilder()
						.useComponentsV2()
						.mention(event.getUser())
						.addComponents(Messages.getMessageComponents("discord-info-player-not-found"))
						.build();
			}

			return preparePlayerInfoResponse(result.getPlayerInfo(), discordFuture.join());
		}).thenAccept(message -> event.getHook().sendMessage(message).queue()).exceptionally(e -> {
			plugin.getLogger().warn("Failed to respond to interaction", e);

			event.getHook().sendMessage(
					new MessageCreateBuilder()
							.useComponentsV2()
							.mention(event.getUser())
							.addComponents(Messages.getMessageComponents("discord-info-error"))
							.build())
					.queue();

			return null;
		});
	}

	private MessageCreateData preparePlayerInfoResponse(PlayerInfo info, User discordUser) {
		String discord = discordUser != null ?
				Messages.get("discord-info-discord-linked",
							 Collections.singletonMap("<discord>", discordUser.getAsMention())) :
				Messages.get("discord-info-discord-not-linked");

		String status = Messages.get("discord-info-status-offline");
		VerificationResult verifyStatus = discordUser != null ?
				verificationManager.checkVerificationStatus(discordUser) :
				VerificationResult.NOT_LINKED;

		String access = switch (verifyStatus) {
			case UNKNOWN, NOT_LINKED -> Messages.get("discord-info-access-not-linked");
			case LINKED_NOT_VERIFIED -> Messages.get("discord-info-access-linked-missing-roles");
			case VERIFIED -> {
				Set<Role> verifiedRoles = verificationManager.getVerifiedRoles();
				Map<Guild, Set<Role>> guildRoles = new HashMap<>();

				verifiedRoles.stream().map(Role::getGuild).distinct().forEach(g -> {
					Member member = g.getMember(discordUser);
					guildRoles.put(g, member != null ? member.getUnsortedRoles() : Collections.emptySet());
				});

				String roles = verificationManager.getVerifiedRoles().stream()
						.filter(r -> guildRoles.get(r.getGuild()).contains(r))
						.map(Role::getAsMention).collect(Collectors.joining(", "));

				yield Messages.get("discord-info-access-linked-roles", Collections.singletonMap("<roles>", roles));
			}
			case BYPASSED -> Messages.get("discord-info-access-linked-bypassed");
			case NOT_REQUIRED -> Messages.get("discord-info-access-not-configured");
		};

		if (info.isOnline() && !info.isVanished()) {
			if(info.getQueueInfo() != null) {
				status = Messages.get("discord-info-status-queueing", Map.of(
						"<server>", info.getQueueInfo().server().getServerInfo().getName(),
						"<position>", info.getQueueInfo().position()));
			} else if (info.getCurrentServer() != null) {
				status = Messages.get("discord-info-status-online-server", Collections.singletonMap(
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

		return new MessageCreateBuilder()
				.useComponentsV2()
				.addComponents(Messages.getMessageComponents("discord-info-success", replacements))
				.build();
	}
}