package uk.co.notnull.proxydiscord;

import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;

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
}
