package uk.co.notnull.proxydiscord;

import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.model.user.User;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.jetbrains.annotations.NotNull;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;

import java.util.Map;
import java.util.regex.Pattern;

public class Util {
	private static final Pattern unescapePattern = Pattern.compile("\\\\([*_`~\\\\])");
	private static final Pattern escapePattern = Pattern.compile("([*_`~\\\\])");

	public static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
          .extractUrls(Style.style().color(TextColor.fromHexString("#8194e4"))
							   .decoration(TextDecoration.UNDERLINED, true).build())
          .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

	public static final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();

	public static String escapeMarkdown(String message) {
		message = unescapePattern.matcher(message).replaceAll("$1");
		return escapePattern.matcher(message).replaceAll("\\\\$1");
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

	public static String stripFormatting(String message) {
		return plainSerializer.serialize(legacySerializer.deserialize(message));
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
				.replace(find, Util.escapeMarkdown(replace)));

        ref.message = ref.message.replace("[server]", Util.escapeMarkdown(serverName));
        ref.message = ref.message.replace("[player]", Util.escapeMarkdown(entry.getPlayer().getUsername()));
        ref.message = ref.message.replace("[discord_id]", discordId != null ? String.valueOf(discordId) : "Unlinked");
        ref.message = ref.message.replace("[discord_mention]", discordId != null ? "<@!" + discordId + ">" : "");

        return ref.message;
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
