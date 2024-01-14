/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2023 James Lyne
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

package uk.co.notnull.proxydiscord.logging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.spongepowered.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.emoji.CustomEmoji;
import org.javacord.api.entity.emoji.KnownCustomEmoji;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.MessageBuilder;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.Util;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;
import uk.co.notnull.proxydiscord.api.logging.LogType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class LoggingFormatter {
	private final Logger logger;
	private final Map<LogType, String> formats = new HashMap<>();
	private SimpleDateFormat dateFormat;
	private boolean codeBlock = false;
	private String ingameChatFormat = "";

	public LoggingFormatter(ConfigurationNode logFormats, ConfigurationNode defaultLogFormats) {
		this.logger = ProxyDiscord.inst().getLogger();
		parseConfig(logFormats, defaultLogFormats);
	}

	private void parseConfig(ConfigurationNode logFormats, ConfigurationNode defaultLogFormats) {
		if(logFormats.isMap() || defaultLogFormats.isMap()) {
            ConfigurationNode dateFormat = logFormats.node("date");
            ConfigurationNode codeBlock = logFormats.node("code-block");
            ConfigurationNode chatFormat = logFormats.node("chat");
            ConfigurationNode discordChatFormat = logFormats.node("discord-chat");
            ConfigurationNode joinFormat = logFormats.node("join");
            ConfigurationNode leaveFormat = logFormats.node("leave");
            ConfigurationNode commandFormat = logFormats.node("command");
            ConfigurationNode ingameChatFormat = logFormats.node("discord-chat-ingame");

            ConfigurationNode defaultDateFormat = defaultLogFormats.node("date");
            ConfigurationNode defaultCodeBlock = defaultLogFormats.node("code-block");
            ConfigurationNode defaultChatFormat = defaultLogFormats.node("chat");
            ConfigurationNode defaultDiscordChatFormat = defaultLogFormats.node("discord-chat");
            ConfigurationNode defaultJoinFormat = defaultLogFormats.node("join");
            ConfigurationNode defaultLeaveFormat = defaultLogFormats.node("leave");
            ConfigurationNode defaultCommandFormat = defaultLogFormats.node("command");
            ConfigurationNode defaultIngameChatFormat = defaultLogFormats.node("discord-chat-ingame");

            try {
                 this.dateFormat = new SimpleDateFormat(dateFormat.getString(defaultDateFormat.getString("")));
            } catch(IllegalArgumentException e) {
                logger.warn("Invalid logging date format: " + e.getMessage());
            }

			this.codeBlock = codeBlock.getBoolean(defaultCodeBlock.getBoolean(false));

            formats.put(LogType.CHAT, chatFormat.getString(defaultChatFormat.getString("")));
            formats.put(LogType.DISCORD_CHAT, discordChatFormat.getString(defaultDiscordChatFormat.getString("")));
            formats.put(LogType.JOIN, joinFormat.getString(defaultJoinFormat.getString("")));
            formats.put(LogType.LEAVE, leaveFormat.getString(defaultLeaveFormat.getString("")));
            formats.put(LogType.COMMAND, commandFormat.getString(defaultCommandFormat.getString("")));
            this.ingameChatFormat = ingameChatFormat.getString(defaultIngameChatFormat.getString(""));
        }
	}

	/**
	 * Formats the given {@link LogEntry} into a string ready for logging, using the appropriate log format
	 * A standard set of replacements are applied to the format, in addition to any replacements defined in the log entry
	 * @param entry The log entry to format
	 * @return the formatted string
	 */
	public String formatLogEntry(@NonNull LogEntry entry) {
		var ref = new Object() {
			String message = formats.get(entry.getType());
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
				r = addEmoteMentions(r);
			}

			ref.message = ref.message.replace("<" + f + ">", sanitiseLogMessage(r));
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
					org.javacord.api.entity.user.User::getName).orElse("Unknown"));
		} else {
			replace.apply("discord_id", "Unlinked");
			replace.apply("discord_mention", "Unlinked");
			replace.apply("discord_username", "Unlinked");
		}

		entry.getReplacements().forEach(safeReplace::apply);

		return codeBlock ? "```" + ref.message + " ```" : ref.message;
	}

	/**
	 * Replaces :emote: syntax in the given string with a Discord emote mention, if the emote name matches any known
	 * custom emotes usable by the bot. Managed emotes are ignored as bots appear to be unable to use these.
	 * @param message The message
	 * @return the message with emote mentions added
	 */
	private static String addEmoteMentions(String message) {
		DiscordApi api = ProxyDiscord.inst().getDiscord().getApi();

		return Util.emotePattern.matcher(message).replaceAll(result -> {
			Optional<KnownCustomEmoji> emoji = api.getCustomEmojisByNameIgnoreCase(
					result.group(1).replace("\\", ""))
					.stream().filter(e -> !e.isManaged()).findFirst();
			return emoji.map(CustomEmoji::getMentionTag).orElse(result.group());
		});
	}

	/**
	 * Escapes markdown formatting (if necessary) and strips Minecraft formatting from the given message.
	 * @param message The message
	 * @return The prepared message
	 */
	private String sanitiseLogMessage(String message) {
		if(codeBlock) {
			return Util.plainSerializer.serialize(Util.miniMessage.deserialize(message)).replace("```", "");
		} else {
			return Util.plainStripMarkdownSerializer.serialize(Util.miniMessage.deserialize(message));
		}
	}

	/**
	 * Formats a discord message for logging, using the appropriate log format
	 * A standard set of replacements are applied to the format, in addition to any provided ones.
	 * @param user The luckperms user associated with the message
	 * @param message The discord message object
	 * @param messageContent The content of the message
	 * @return the formatted string
	 */
	public String formatDiscordMessageLog(@NonNull User user, @NonNull Message message, String messageContent) {
		var ref = new Object() {
			String result = formats.get(LogType.DISCORD_CHAT);
		};

		if(ref.result.isEmpty()) {
			return null;
		}

		MessageAuthor author = message.getAuthor();
		messageContent = Util.plainSerializer.serialize(Util.plainSerializer.deserialize(messageContent)); //FIXME: Needed?

		String date = dateFormat != null ? dateFormat.format(new Date()) : "";
        ref.result = ref.result.replace("<date>", date);

		ref.result = ref.result.replace("<server>", "Discord");
		ref.result = ref.result.replace("<player>", user.getFriendlyName());
		ref.result = ref.result.replace("<uuid>", user.getUniqueId().toString());

		ref.result = ref.result.replace("<discord_id>", author.getIdAsString());
		ref.result = ref.result.replace("<discord_mention>", "<@!" + author.getIdAsString() + ">");
		ref.result = ref.result.replace("<discord_username>", author.getName());
		ref.result = ref.result.replace("<message>", messageContent);
		ref.result = ref.result.replace("<attachments>", formatDiscordMessageAttachments(message));
		ref.result = ref.result.replace("<0x1b>", new StringBuilder().appendCodePoint(0x1b).toString());

        return codeBlock ? "```" + ref.result.replace("```", "") + " ```" : ref.result;
	}

	/**
	 * Formats a message's attachments.
	 * @param message The message to format the attachments for
	 * @return the formatted string
	 */
	private static String formatDiscordMessageAttachments(Message message) {
		StringBuilder result = new StringBuilder();
		boolean first = true;

		for(MessageAttachment attachment: message.getAttachments()) {
			// Put each attachment on new line
			if(!first || !message.getContent().isEmpty()) {
				result.append("\n");
			}

			first = false;
			result.append(formatDiscordMessageAttachment(attachment));
		}

		return result.toString();
	}

	/**
	 * Formats a message attachment.
	 * @param attachment The attachment to format
	 * @return the formatted string
	 */
	private static String formatDiscordMessageAttachment(MessageAttachment attachment) {
		String result = Messages.get("attachment-log");

		if(result.isEmpty()) {
			return null;
		}

		String type = attachment.isImage() ? Messages.get("attachment-type-image") : Messages.get("attachment-type-generic");
		String typeIcon = attachment.isImage() ? Messages.get("attachment-type-image-icon") : Messages.get("attachment-type-generic-icon");

		result = result.replace("<type>", type);
		result = result.replace("<type_icon>", typeIcon);
		result = result.replace("<filename>", attachment.getFileName());
		result = result.replace("<url>", attachment.getUrl().toString());

        return result;
	}

	/**
	 * Formats a discord message for display in-game, using the in-game chat format
	 * A standard set of replacements are applied to the format, in addition to any provided ones.
	 * @param ingameUser The Luckperms user associated with the message
	 * @param message The discord message object
	 * @param messageContent The content of the message
	 * @return the formatted Component
	 */
	public Component formatDiscordMessageIngame(User ingameUser, Message message, String messageContent) {
		if(ingameChatFormat.isEmpty()) {
			return null;
		}

		MessageAuthor author = message.getAuthor();
		CachedMetaData metaData = ingameUser.getCachedData().getMetaData(QueryOptions.nonContextual());
		String prefix = ( metaData.getPrefix() != null) ?  metaData.getPrefix() : "";
		String suffix = ( metaData.getSuffix() != null) ?  metaData.getSuffix() : "";

		TagResolver.Builder placeholders = TagResolver.builder();

		placeholders.resolver(Placeholder.parsed("prefix", prefix));
		placeholders.resolver(Placeholder.parsed("suffix", suffix));
        placeholders.resolver(Placeholder.unparsed("player", ingameUser.getFriendlyName()));
        placeholders.resolver(Placeholder.unparsed("uuid", ingameUser.getUniqueId().toString()));
        placeholders.resolver(Placeholder.unparsed("discord_id", author.getIdAsString()));
        placeholders.resolver(Placeholder.unparsed("discord_username", author.getName()));
		placeholders.resolver(Placeholder.component("message", Util.prepareDiscordMessage(messageContent)));
		placeholders.resolver(Placeholder.component("attachments", Util.prepareDiscordMessageAttachments(message)));

		return Util.miniMessage.deserialize(ingameChatFormat, placeholders.build());
	}


	/**
	 * Appends the given message to the given MessageBuilder, if there is sufficient space to do so without exceeding
	 * Discord's character limit
	 * @param currentMessage The MessageBuilder to append to
	 * @param newMessage The message to append
	 * @return Whether the message can be appended
	 */
	public boolean appendMessage(MessageBuilder currentMessage, String newMessage) {
		int currentLength  = currentMessage.getStringBuilder().length();

        if(currentLength > 0 && currentLength + newMessage.length() > 2000) {
        	return false;
        }

        if(currentLength > 0 && !codeBlock) {
        	currentMessage.append("\n");
		}

        currentMessage.append(newMessage);

		return true;
	}

	/**
	 * Truncates the given message if it exceeds Discord's character limit
	 * @param message The message to truncate
	 * @return The possibly truncated message
	 */
	public String truncateMessage(String message) {
		if(message.length() > 2000) {
        	message = message.substring(0, 1991) + "[...]";

        	if(codeBlock) {
        		message = message + "```";
			}
		}

		return message;
	}

	public boolean hasFormat(LogType type) {
		return !formats.getOrDefault(type, "").isEmpty();
	}
}
