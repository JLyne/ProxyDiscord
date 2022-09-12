/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2021 James Lyne
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

import com.velocitypowered.api.proxy.Player;
import dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializer;
import dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializerOptions;
import dev.vankka.mcdiscordreserializer.rules.DiscordMarkdownRules;
import dev.vankka.simpleast.core.parser.Parser;
import dev.vankka.simpleast.core.simple.SimpleMarkdownRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.model.user.User;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.emoji.CustomEmoji;
import org.javacord.api.entity.emoji.KnownCustomEmoji;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageAuthor;
import org.jetbrains.annotations.NotNull;
import uk.co.notnull.proxydiscord.api.emote.EmoteProvider;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;
import uk.co.notnull.proxydiscord.renderer.CustomMinecraftRenderer;
import uk.co.notnull.proxydiscord.emote.DefaultEmoteProvider;

import java.util.Collections;
import java.util.List;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Util {
	private static final Pattern markdownPattern = Pattern.compile("([*_|`~])");
	private static final Pattern emotePattern = Pattern.compile(":([\\w\\\\]{2,}):");
	private static final Pattern sectionPattern = Pattern.compile("\\u00A7[\\da-gA-Gk-oK-OrR]");

	private final static Pattern validUUIDPattern =
			Pattern.compile("^[{]?[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}[}]?$");

	/**
	 * Component flattener which escapes markdown syntax present in any components that aren't clickable links
	 */
	public static final ComponentFlattener stripMarkdownFlattener = ComponentFlattener.builder()
			.mapper(TextComponent.class, (theComponent) -> {
				ClickEvent event = theComponent.clickEvent();

				if (event != null && event.action() == ClickEvent.Action.OPEN_URL) {
					return theComponent.content();
				} else {
					return markdownPattern.matcher(theComponent.content()).replaceAll("\\\\$1");
				}
			}).build();

	public static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
          .extractUrls().character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

	public static final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
	public static final PlainTextComponentSerializer plainStripMarkdownSerializer = PlainTextComponentSerializer
			.builder().flattener(stripMarkdownFlattener).build();

	public static final MinecraftSerializer markdownSerializer = new MinecraftSerializer(
			new MinecraftSerializerOptions<>(
					new Parser<>(),
					List.of(
							DiscordMarkdownRules.createQuoteRule(),
							DiscordMarkdownRules.createSpoilerRule(),
							DiscordMarkdownRules.createCodeBlockRule(),
							DiscordMarkdownRules.createCodeStringRule(),
							SimpleMarkdownRules.createEscapeRule(),
							SimpleMarkdownRules.createLinkRule(),
							SimpleMarkdownRules.createNewlineRule(),
							SimpleMarkdownRules.createBoldRule(),
							SimpleMarkdownRules.createUnderlineRule(),
							SimpleMarkdownRules.createItalicsRule(),
							SimpleMarkdownRules.createStrikethruRule(),
							SimpleMarkdownRules.createTextRule()
					),
					Collections.singletonList(new CustomMinecraftRenderer()),
					false
			));

	public static final MiniMessage miniMessage = MiniMessage.miniMessage();
	public static final EmoteProvider defaultEmoteProvider = new DefaultEmoteProvider();

	public static final TextReplacementConfig emoteReplacement = TextReplacementConfig.builder()
			.match(Util.emotePattern)
			.replacement((MatchResult match, TextComponent.Builder builder) ->
								 ProxyDiscord.inst().getEmoteProvider().provide(match.group(1), builder)).build();

	/**
	 * Gets the content of the given Discord message.
	 * The URLs of any present attachments will be appended to the content.
	 * @param message The message
	 * @return the message content
	 */
	public static String getDiscordMessageContent(Message message) {
		StringBuilder text = new StringBuilder(message.getReadableContent());

		for (MessageAttachment attachment : message.getAttachments()) {
			if(text.length() > 0) {
				text.append(" ");
			}

			text.append(attachment.getUrl().toString()).append(" ");
		}

		return text.toString();
	}

	/**
	 * Returns a URL for the given discord server channel
	 * @param channel The channel
	 * @return The URL
	 */
	public static String getDiscordChannelURL(ServerChannel channel) {
		return String.format("https://discord.com/channels/%s/%s",
							 channel.getServer().getIdAsString(), channel.getIdAsString());
	}

	/**
	 * Returns a URL for the given discord message
	 * @param message The message
	 * @return The URL
	 */
	public static String getDiscordMessageURL(Message message) {
		Optional<ServerChannel> serverChannel = message.getChannel().asServerChannel();

		if(serverChannel.isPresent()) {
			return String.format("https://discord.com/channels/%s/%s/%s",
								 serverChannel.get().getServer().getIdAsString(),
								 serverChannel.get().getIdAsString(),
								 message.getIdAsString());
		} else {
			return String.format("https://discord.com/channels/@me/%s/%s", message.getChannel().getIdAsString(),
								 message.getIdAsString());
		}
	}

	/**
	 * Formats the given {@link LogEntry} into a string using the given format
	 * A standard set of replacements are applied to the format, in addition to any replacements defined in the log entry
	 * @param format The format to use
	 * @param entry The log entry to format
	 * @return the formatted string
	 */
	public static String formatLogEntry(@NotNull String format, DateFormat dateFormat, boolean codeBlock, @NonNull LogEntry entry) {
		var ref = new Object() {
			String message = format;
		};

		if(ref.message.isEmpty()) {
			return null;
		}

		BiFunction<String, String, Void> replace = (String f, String r) -> {
			ref.message = ref.message.replace("<" + f + ">", r);
			return null;
		};

		BiFunction<String, String, Void> safeReplace = (String f, String r) -> {
			if (!codeBlock) {
				r = Util.formatDiscordEmotes(r);
			}

			ref.message = ref.message.replace("<" + f + ">", Util.prepareLogMessage(r, codeBlock));
			return null;
		};

        Long discordId = ProxyDiscord.inst().getLinkingManager().getLinked(entry.getPlayer());
		Optional<org.javacord.api.entity.user.User> discordUser = Optional.empty();

		if(discordId != null) {
			discordUser = ProxyDiscord.inst().getDiscord().getApi().getCachedUserById(discordId);
		}

        replace.apply("date", dateFormat != null ? dateFormat.format(new Date()) : "");
		replace.apply("uuid", entry.getPlayer().getUniqueId().toString());
		replace.apply("0x1b", new StringBuilder().appendCodePoint(0x1b).toString());

		safeReplace.apply("server", entry.getServer()
				.map(server -> server.getServerInfo().getName())
				.orElse("unknown"));
		safeReplace.apply("player", entry.getPlayer().getUsername());

		if (discordId != null) {
			replace.apply("discord_id", String.valueOf(discordId));
			replace.apply("discord_mention", "<@!" + discordId + ">");
			replace.apply("discord_username", discordUser.map(
					org.javacord.api.entity.user.User::getDiscriminatedName).orElse("Unknown"));
		} else {
			replace.apply("discord_id", "Unlinked");
			replace.apply("discord_mention", "Unlinked");
			replace.apply("discord_username", "Unlinked");
		}

		entry.getReplacements().forEach(safeReplace::apply);

		return codeBlock ? "```" + ref.message + " ```" : ref.message;
	}

	/**
	 * Escapes markdown formatting (if necessary) and strips Minecraft formatting from the given message.
	 * @param message The message
	 * @param codeBlock Whether the log message will be sent within a code block, meaning that markdown
	 *                    escaping shouldn't occur
	 * @return The prepared message
	 */
	public static String prepareLogMessage(String message, boolean codeBlock) {
		if(codeBlock) {
			return plainSerializer.serialize(legacySerializer.deserialize(message)).replace("```", "");
		} else {
			return plainStripMarkdownSerializer.serialize(legacySerializer.deserialize(message));
		}
	}

	/**
	 * Replaces :emote: syntax in the given string with a Discord emote mention, if the emote name matches any known
	 * custom emotes usable by the bot. Managed emotes are ignored as bots appear to be unable to use these.
	 * @param message The message
	 * @return the message with emote mentions added
	 */
	public static String formatDiscordEmotes(String message) {
		DiscordApi api = ProxyDiscord.inst().getDiscord().getApi();

		return emotePattern.matcher(message).replaceAll(result -> {
			Optional<KnownCustomEmoji> emoji = api.getCustomEmojisByNameIgnoreCase(
					result.group(1).replace("\\", ""))
					.stream().filter(e -> !e.isManaged()).findFirst();
			return emoji.map(CustomEmoji::getMentionTag).orElse(result.group());
		});
	}

	/**
	 * Formats a message associated with the given Luckperms user using the given format and replacements.
	 * A standard set of replacements are applied to the format, in addition to any provided ones.
	 * @param format The format to use
	 * @param user The luckperms user associated with the message
	 * @param replacements Additional replacements to apply
	 * @return the formatted string
	 */
	public static String formatDiscordMessage(
			@NonNull String format, DateFormat dateFormat, boolean codeBlock, @NonNull User user,
			@NonNull MessageAuthor author,
			@NonNull Map<String, String> replacements) {
		var ref = new Object() {
			String message = format;
		};

		if(ref.message.isEmpty()) {
			return null;
		}

		String date = dateFormat != null ? dateFormat.format(new Date()) : "";
        ref.message = ref.message.replace("<date>", date);

		ref.message = ref.message.replace("<server>", "Discord");
		ref.message = ref.message.replace("<player>", user.getFriendlyName());
		ref.message = ref.message.replace("<uuid>", user.getUniqueId().toString());

		ref.message = ref.message.replace("<discord_id>", author.getIdAsString());
		ref.message = ref.message.replace("<discord_mention>", "<@!" + author.getIdAsString() + ">");
		ref.message = ref.message.replace("<discord_username>", author.getDiscriminatedName());
		ref.message = ref.message.replace("<0x1b>", new StringBuilder().appendCodePoint(0x1b).toString());

		replacements.forEach((String find, String replace) -> ref.message = ref.message
				.replace("<" + find + ">", replace));

        return codeBlock ? "```" + ref.message.replace("```", "") + " ```" : ref.message;
	}

	/**
	 * Removes any section-character formatting from the given string then deserializes it into a
	 * component with formatted extracted URLs
	 * @param message The message
	 * @return The prepared message
	 */
	public static Component prepareDiscordMessage(String message) {
		return markdownSerializer.serialize(sectionPattern.matcher(message).replaceAll(""))
				.replaceText(emoteReplacement);
	}

	public static boolean isValidUUID(String uuid) {
		return validUUIDPattern.asMatchPredicate().test(uuid);
	}

	public static Stream<Player> getPlayerSuggestions(String query) {
		SuperVanishBridgeHandler superVanishBridgeHandler = ProxyDiscord.inst().getSuperVanishBridgeHandler();

		return ProxyDiscord.inst().getProxy().matchPlayer(query).stream()
				.filter(player -> superVanishBridgeHandler == null || !superVanishBridgeHandler.isVanished(player));
	}
}
