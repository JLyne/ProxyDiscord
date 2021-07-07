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

import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import net.luckperms.api.model.user.User;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.emoji.CustomEmoji;
import org.javacord.api.entity.emoji.KnownCustomEmoji;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageAuthor;
import org.jetbrains.annotations.NotNull;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class Util {
	private static final Pattern markdownPattern = Pattern.compile("([*_|`~])");
	private static final Pattern emotePattern = Pattern.compile(":([\\w\\\\]{2,}):");
	private static final Pattern sectionPattern = Pattern.compile("\\u00A7[0-9a-gA-Gk-oK-OrR]");

	/**
	 * Component flattener which escapes markdown syntax present in any components that aren't clickable links
	 */
	private static final ComponentFlattener stripMarkdownFlattener = ComponentFlattener.builder()
			.mapper(TextComponent.class, (theComponent) -> {
				ClickEvent event = theComponent.clickEvent();

				if (event != null && event.action() == ClickEvent.Action.OPEN_URL) {
					return theComponent.content();
				} else {
					return markdownPattern.matcher(theComponent.content()).replaceAll("\\\\$1");
				}
			}).build();

	public static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
          .extractUrls(Style.style().color(TextColor.fromHexString("#8194e4"))
							   .decoration(TextDecoration.UNDERLINED, true).build())
          .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

	public static final PlainComponentSerializer plainSerializer = PlainComponentSerializer.plain();
	public static final PlainComponentSerializer plainStripMarkdownSerializer = PlainComponentSerializer
			.builder().flattener(stripMarkdownFlattener).build();

	/**
	 * Tries its best to escape markdown formatting and strip Minecraft formatting from the given message.
	 * @param message The message
	 * @return the escaped message
	 */
	public static String escapeFormatting(String message) {
		return plainStripMarkdownSerializer.serialize(legacySerializer.deserialize(message));
	}

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
	 * Removes any section-character or &-based formatting from the given string
	 * @param message The message
	 * @return the message with any section-character formatting removed
	 */
	public static String stripFormatting(String message) {
		return sectionPattern.matcher(plainSerializer.serialize(legacySerializer.deserialize(message)))
				.replaceAll("");
	}

	/**
	 * Formats the given {@link LogEntry} into a string using the given format
	 * A standard set of replacements are applied to the format, in addition to any replacements defined in the log entry
	 * @param format The format to use
	 * @param entry The log entry to format
	 * @return the formatted string
	 */
	public static String formatLogEntry(@NotNull String format, @NonNull LogEntry entry) {
		var ref = new Object() {
			String message = format;
		};

		if(ref.message.isEmpty()) {
			return null;
		}

        Long discordId = ProxyDiscord.inst().getLinkingManager().getLinked(entry.getPlayer());
		Optional<org.javacord.api.entity.user.User> discordUser = Optional.empty();

		if(discordId != null) {
			discordUser = ProxyDiscord.inst().getDiscord().getApi().getCachedUserById(discordId);
		}

        String serverName = entry.getServer().map(server -> server.getServerInfo().getName()).orElse("unknown");

        entry.getReplacements().forEach((String find, String replace) -> ref.message = ref.message
				.replace(find, Util.formatEmotes(Util.escapeFormatting(replace))));

        ref.message = ref.message.replace("[server]", Util.escapeFormatting(serverName));
        ref.message = ref.message.replace("[player]", Util.escapeFormatting(entry.getPlayer().getUsername()));
        ref.message = ref.message.replace("[uuid]", entry.getPlayer().getUniqueId().toString());

        if(discordId != null) {
			ref.message = ref.message.replace("[discord_id]", String.valueOf(discordId));
			ref.message = ref.message.replace("[discord_mention]", "<@!" + discordId + ">");
			ref.message = ref.message.replace("[discord_username]", discordUser.map(
					org.javacord.api.entity.user.User::getDiscriminatedName).orElse("Unknown"));
		} else {
        	ref.message = ref.message.replace("[discord_id]", "Unlinked");
			ref.message = ref.message.replace("[discord_mention]", "Unlinked");
			ref.message = ref.message.replace("[discord_username]", "Unlinked");
		}

        return ref.message;
	}

	/**
	 * Replaces :emote: syntax in the given string with a Discord emote mention, if the emote name matches any known
	 * custom emotes usable by the bot. Managed emotes are ignored as bots appear to be unable to use these.
	 * @param message The message
	 * @return the message with emote mentions added
	 */
	private static String formatEmotes(String message) {
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
	public static String formatDiscordMessage(@NonNull String format, @NonNull User user,
											  @NonNull MessageAuthor author,
											  @NonNull Map<String, String> replacements) {
		var ref = new Object() {
			String message = format;
		};

		if(ref.message.isEmpty()) {
			return null;
		}

        replacements.forEach((String find, String replace) -> ref.message = ref.message
				.replace(find, replace));

        ref.message = ref.message.replace("[server]", "Discord");
        ref.message = ref.message.replace("[player]", user.getFriendlyName());
        ref.message = ref.message.replace("[uuid]", user.getUniqueId().toString());

		ref.message = ref.message.replace("[discord_id]", author.getIdAsString());
		ref.message = ref.message.replace("[discord_mention]", "<@!" + author.getIdAsString() + ">");
		ref.message = ref.message.replace("[discord_username]", author.getDiscriminatedName());

        return ref.message;
	}
}
