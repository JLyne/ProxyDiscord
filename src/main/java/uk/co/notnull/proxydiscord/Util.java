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

import dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializer;
import dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializerOptions;
import dev.vankka.mcdiscordreserializer.rules.DiscordMarkdownRules;
import dev.vankka.simpleast.core.parser.Parser;
import dev.vankka.simpleast.core.simple.SimpleMarkdownRules;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.entities.messages.MessageSnapshot;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import uk.co.notnull.proxydiscord.api.emote.EmoteProvider;
import uk.co.notnull.proxydiscord.markdown.CustomMarkdownRules;
import uk.co.notnull.proxydiscord.markdown.CustomMinecraftRenderer;
import uk.co.notnull.proxydiscord.emote.DefaultEmoteProvider;

import java.util.Collections;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public class Util {
	private static final Pattern markdownPattern = Pattern.compile("([*_|`~])");
	public static final Pattern emotePattern = Pattern.compile(":([\\w\\\\]{2,}):");
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

	public static final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
	public static final PlainTextComponentSerializer plainStripMarkdownSerializer = PlainTextComponentSerializer
			.builder().flattener(stripMarkdownFlattener).build();

	public static final MinecraftSerializer markdownSerializer = new MinecraftSerializer(
			new MinecraftSerializerOptions<>(
					new Parser<>(),
					List.of(
							SimpleMarkdownRules.createEscapeRule(),
							CustomMarkdownRules.createLinkRule(),
							SimpleMarkdownRules.createNewlineRule(),
							DiscordMarkdownRules.createBoldRule(),
							DiscordMarkdownRules.createUnderlineRule(),
							DiscordMarkdownRules.createItalicsRule(),
							DiscordMarkdownRules.createStrikethruRule(),

							//	DiscordMarkdownRules.createQuoteRule(), //Seems broken atm
							DiscordMarkdownRules.createSpoilerRule(),
							DiscordMarkdownRules.createCodeBlockRule(),
							DiscordMarkdownRules.createCodeStringRule(),
							CustomMarkdownRules.createTimestampRule(),
//							DiscordMarkdownRules.createSpecialTextRule(), //Needed for quotes
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

	public static boolean isMessageEmpty(Message message) {
		return message.getContentStripped().isBlank()
				&& message.getAttachments().isEmpty()
				&& message.getStickers().isEmpty()
				&& message.getMessageSnapshots().isEmpty();
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

	public static Component prepareDiscordMessageAttachments(Message message) {
		TextComponent.@NotNull Builder result = Component.text();
		boolean first = true;
		boolean emptyMessage = message.getContentStripped().isEmpty();

		for(MessageSnapshot snapshot: message.getMessageSnapshots()) {
			// Put each snapshot on new line
			if(!first || !emptyMessage) {
				result.append(Component.newline());
			}

			first = false;
			result.append(prepareDiscordMessageSnapshot(snapshot, message.getMessageReference(), message.getJumpUrl()));
		}

		for(Message.Attachment attachment: message.getAttachments()) {
			// Put each attachment on new line
			if(!first || !emptyMessage) {
				result.append(Component.newline());
			}

			first = false;
			result.append(prepareDiscordMessageAttachment(attachment, message.getJumpUrl()));
		}

		for(StickerItem sticker: message.getStickers()) {
			// Put each sticker on new line
			if(!first || !emptyMessage) {
				result.append(Component.newline());
			}

			first = false;
			result.append(prepareDiscordMessageSticker(sticker, message.getJumpUrl()));
		}

		return result.build();
	}

	public static Component prepareDiscordMessageSnapshot(MessageSnapshot snapshot, @Nullable MessageReference reference, String jumpUrl) {
		boolean first = true;
		boolean emptyMessage = snapshot.getContentRaw().isEmpty();
		TagResolver.@NotNull Builder placeholders = TagResolver.builder();

		Component message = prepareDiscordMessage(snapshot.getContentRaw());
		TextComponent.@NotNull Builder attachments = Component.text();

		for(Message.Attachment attachment: snapshot.getAttachments()) {
			// Put each attachment on new line
			if(!first || !emptyMessage) {
				attachments.append(Component.newline());
			}

			first = false;
			attachments.append(prepareDiscordMessageAttachment(attachment, jumpUrl));
		}

		for(StickerItem sticker: snapshot.getStickers()) {
			// Put each sticker on new line
			if(!first || !emptyMessage) {
				attachments.append(Component.newline());
			}

			first = false;
			attachments.append(prepareDiscordMessageSticker(sticker, jumpUrl));
		}

		String channelName = reference != null && reference.getChannel() != null ? reference.getChannel().getName() : "Unknown Channel";
		String serverName = reference != null && reference.getGuild() != null ? reference.getGuild().getName() : "Unknown Server";
		message = message.append(attachments.build());

		placeholders.resolver(Placeholder.unparsed("server", serverName));
		placeholders.resolver(Placeholder.unparsed("channel", channelName));
		placeholders.resolver(Placeholder.component("message", message));
		placeholders.resolver(Placeholder.unparsed("message_link", jumpUrl));

		return miniMessage.deserialize(Messages.get("forwarded-message"), placeholders.build());
	}

	public static Component prepareDiscordMessageAttachment(Message.Attachment attachment, String jumpUrl) {
		TagResolver.@NotNull Builder placeholders = TagResolver.builder();
		String type = attachment.isImage() ? Messages.get("attachment-type-image") : Messages.get("attachment-type-generic");
		String typeIcon = attachment.isImage() ? Messages.get("attachment-type-image-icon") : Messages.get("attachment-type-generic-icon");

		placeholders.resolver(Placeholder.unparsed("filename", attachment.getFileName()));
		placeholders.resolver(Placeholder.styling("link", ClickEvent.openUrl(attachment.getUrl())));
		placeholders.resolver(Placeholder.unparsed("message_link", jumpUrl));
		placeholders.resolver(Placeholder.unparsed("url", attachment.getUrl()));
		placeholders.resolver(Placeholder.unparsed("type", type));
		placeholders.resolver(Placeholder.unparsed("type_icon", typeIcon));

		return miniMessage.deserialize(Messages.get("attachment"), placeholders.build());
	}

	public static Component prepareDiscordMessageSticker(StickerItem sticker, String jumpUrl) {
		TagResolver.@NotNull Builder placeholders = TagResolver.builder();

		placeholders.resolver(Placeholder.unparsed("name", sticker.getName()));
		placeholders.resolver(Placeholder.styling("link", ClickEvent.openUrl(sticker.getIconUrl())));
		placeholders.resolver(Placeholder.styling("message_link", ClickEvent.openUrl(jumpUrl)));
		placeholders.resolver(Placeholder.unparsed("url", sticker.getIconUrl()));

		return miniMessage.deserialize(
				sticker.getFormatType() == Sticker.StickerFormat.LOTTIE ?
						Messages.get("sticker-lottie") : Messages.get("sticker"),
				placeholders.build());
	}

	public static boolean isValidUUID(String uuid) {
		return validUUIDPattern.asMatchPredicate().test(uuid);
	}
}
