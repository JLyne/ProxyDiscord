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
import org.jetbrains.annotations.NotNull;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class Util {
	private static final Pattern escapePattern = Pattern.compile("([*_|`~])");
	private static final Pattern emotePattern = Pattern.compile(":([\\w\\\\]{2,}):");
	private static final Pattern sectionPattern = Pattern.compile("\\u00A7[0-9a-gA-Gk-oK-OrR]");

	private static final ComponentFlattener stripMarkdownFlattener = ComponentFlattener.builder()
			.mapper(TextComponent.class, (theComponent) -> {
				ClickEvent event = theComponent.clickEvent();

				if (event != null && event.action() == ClickEvent.Action.OPEN_URL) {
					return theComponent.content();
				} else {
					return escapePattern.matcher(theComponent.content()).replaceAll("\\\\$1");
				}
			}).build();

	public static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
          .extractUrls(Style.style().color(TextColor.fromHexString("#8194e4"))
							   .decoration(TextDecoration.UNDERLINED, true).build())
          .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
	public static final PlainComponentSerializer plainSerializer = PlainComponentSerializer.plain();
	public static final PlainComponentSerializer plainStripMarkdownSerializer = PlainComponentSerializer
			.builder().flattener(stripMarkdownFlattener).build();

	public static String escapeFormatting(String message) {
		return plainStripMarkdownSerializer.serialize(legacySerializer.deserialize(message));
	}

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

	public static String stripSectionFormatting(String message) {
		return sectionPattern.matcher(message).replaceAll(result -> {
			ProxyDiscord.inst().getLogger().info(result.group());
			return "";
		});
	}

	public static String formatLogEntry(@NotNull String format, @NonNull LogEntry entry) {
		var ref = new Object() {
			String message = format;
		};

		if(ref.message.isEmpty()) {
			return null;
		}

        Long discordId = ProxyDiscord.inst().getLinkingManager().getLinked(entry.getPlayer());
        String serverName = entry.getServer().map(server -> server.getServerInfo().getName()).orElse("unknown");

        entry.getReplacements().forEach((String find, String replace) -> ref.message = ref.message
				.replace(find, Util.formatEmotes(Util.escapeFormatting(replace))));

        ref.message = ref.message.replace("[server]", Util.escapeFormatting(serverName));
        ref.message = ref.message.replace("[player]", Util.escapeFormatting(entry.getPlayer().getUsername()));
        ref.message = ref.message.replace("[discord_id]", discordId != null ? String.valueOf(discordId) : "Unlinked");
        ref.message = ref.message.replace("[discord_mention]", discordId != null ? "<@!" + discordId + ">" : "");

        return ref.message;
	}

	private static String formatEmotes(String message) {
		DiscordApi api = ProxyDiscord.inst().getDiscord().getApi();

		return emotePattern.matcher(message).replaceAll(result -> {
			Optional<KnownCustomEmoji> emoji = api.getCustomEmojisByNameIgnoreCase(
					result.group(1).replace("\\", ""))
					.stream().filter(e -> !e.isManaged()).findFirst();
			return emoji.map(CustomEmoji::getMentionTag).orElse(result.group());
		});
	}

	public static String formatDiscordMessage(@NonNull String format, @NonNull User user, @NonNull Map<String, String> replacements) {
		var ref = new Object() {
			String message = format;
		};

		if(ref.message.isEmpty()) {
			return null;
		}

        Long discordId = ProxyDiscord.inst().getLinkingManager().getLinked(user.getUniqueId());

        replacements.forEach((String find, String replace) -> ref.message = ref.message
				.replace(find, replace));

        ref.message = ref.message.replace("[server]", "");
        ref.message = ref.message.replace("[player]", user.getFriendlyName());
        ref.message = ref.message.replace("[discord_id]", discordId != null ? String.valueOf(discordId) : "Unlinked");
        ref.message = ref.message.replace("[discord_mention]", discordId != null ? "<@!" + discordId + ">" : "");

        return ref.message;
	}
}
