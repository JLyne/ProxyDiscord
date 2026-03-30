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

package uk.co.notnull.proxydiscord.markdown;

import com.github.marlonlom.utilities.timeago.TimeAgo;
import dev.vankka.mcdiscordreserializer.rules.StyleNode;
import dev.vankka.simpleast.core.node.Node;
import dev.vankka.simpleast.core.parser.ParseSpec;
import dev.vankka.simpleast.core.parser.Parser;
import dev.vankka.simpleast.core.parser.Rule;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomMarkdownRules {
	private static final Pattern PATTERN_LINK = Pattern.compile("^<?https?://(?:(?:\\d{1,3}.){3}\\d{1,3}|[-\\w_.]+\\.\\w{2,})(?:[^\\s>]*)?>?");
	private static final Pattern PATTERN_TIMESTAMP = Pattern.compile("^<t:(\\d+)(?::([dDtTfFsSR]))?>");

    private static <R> StyleNode<R, StyleNode.Style> styleNode(StyleNode.Style style) {
        return new StyleNode<>(new ArrayList<>(Collections.singletonList(style)));
    }

	/**
     * Creates a link rule, for appending the url instead of styling text as an url.
	 * Uses an alternative pattern to handle query strings properly
     * @see dev.vankka.mcdiscordreserializer.rules.DiscordMarkdownRules#createLinkRule()
     */
    public static <R, S> Rule<R, Node<R>, S> createLinkRule() {
        return new Rule<>(PATTERN_LINK) {
			@Override
			public ParseSpec<R, Node<R>, S> parse(Matcher matcher, Parser<R, Node<R>, S> parser, S state) {
				String link = matcher.group();

				// Hack to fix embed suppression <> ending up in the URL
				if(link.startsWith("<")) {
					link = link.substring(1);
				}

				if(link.endsWith(">")) {
					link = link.substring(0, link.length() - 1);
				}

				URI uri;

				try {
					URL url = new URL(URLDecoder.decode(link, StandardCharsets.UTF_8));
					uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
									  url.getPort(), url.getPath(), url.getQuery(), url.getRef());
				} catch (URISyntaxException | MalformedURLException e) {
					return ParseSpec.createTerminal(StyleNode.createWithText(
							link, Collections.emptyList()), state);
				}

				return ParseSpec.createTerminal(
						styleNode(new StyleNode.ContentStyle(StyleNode.ContentStyle.Type.LINK, uri.toString())),
						state
				);
			}
		};
    }

	/**
     * Creates a link rule, for appending the url instead of styling text as an url.
	 * Uses an alternative pattern to handle query strings properly
     * @see dev.vankka.mcdiscordreserializer.rules.DiscordMarkdownRules#createLinkRule()
     */
    public static <R, S> Rule<R, Node<R>, S> createTimestampRule() {
        return new Rule<>(PATTERN_TIMESTAMP) {
			@Override
			public ParseSpec<R, Node<R>, S> parse(Matcher matcher, Parser<R, Node<R>, S> parser, S state) {
				long timestamp = Long.parseLong(matcher.group(1));
				ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());

				TimestampStyle.Timestamp.Format format = switch (matcher.group(2)) {
					case "d" -> TimestampStyle.Timestamp.Format.SHORT_DATE;
					case "D" -> TimestampStyle.Timestamp.Format.LONG_DATE;
					case "t" -> TimestampStyle.Timestamp.Format.SHORT_TIME;
					case "T" -> TimestampStyle.Timestamp.Format.LONG_TIME;
					case "f" -> TimestampStyle.Timestamp.Format.LONG_DATE_SHORT_TIME;
					case "F" -> TimestampStyle.Timestamp.Format.FULL_DATE_SHORT_TIME;
					case "s" -> TimestampStyle.Timestamp.Format.SHORT_DATE_SHORT_TIME;
					case "S" -> TimestampStyle.Timestamp.Format.SHORT_DATE_LONG_TIME;
					case "R" -> TimestampStyle.Timestamp.Format.RELATIVE;
					case null, default -> TimestampStyle.Timestamp.Format.DEFAULT;
				};

				return ParseSpec.createTerminal(
						styleNode(new TimestampStyle(new TimestampStyle.Timestamp(time, format))),
						state
				);
			}
		};
    }

	public static class TimestampStyle implements StyleNode.Style {
        private final Timestamp timestamp;

        public TimestampStyle(Timestamp timestamp) {
            this.timestamp = timestamp;
        }

        public Timestamp getTimestamp() {
            return timestamp;
        }

        @Override
        public String name() {
            return "TIMESTAMP";
        }

		public record Timestamp(ZonedDateTime date, Format format) {
			private static final DateTimeFormatter FORMAT_DEFAULT = DateTimeFormatter.ofPattern("dd LLLL yyyy HH:mm");
			private static final DateTimeFormatter FORMAT_SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
			private static final DateTimeFormatter FORMAT_LONG_DATE = DateTimeFormatter.ofPattern("dd LLLL yyyy");
			private static final DateTimeFormatter FORMAT_SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm");
			private static final DateTimeFormatter FORMAT_LONG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
			private static final DateTimeFormatter FORMAT_LONG_DATE_SHORT_TIME = DateTimeFormatter.ofPattern("dd LLLL yyyy HH:mm");
			private static final DateTimeFormatter FORMAT_FULL_DATE_SHORT_TIME = DateTimeFormatter.ofPattern("EEEE, dd LLLL yyyy HH:mm");
			private static final DateTimeFormatter FORMAT_SHORT_DATE_SHORT_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm");
			private static final DateTimeFormatter FORMAT_SHORT_DATE_LONG_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss");

			public String getFormatted() {
				return switch(format) {
					case SHORT_DATE -> FORMAT_SHORT_DATE.format(date);
					case LONG_DATE -> FORMAT_LONG_DATE.format(date);
					case SHORT_TIME -> FORMAT_SHORT_TIME.format(date);
					case LONG_TIME -> FORMAT_LONG_TIME.format(date);
					case LONG_DATE_SHORT_TIME -> FORMAT_LONG_DATE_SHORT_TIME.format(date);
					case FULL_DATE_SHORT_TIME -> FORMAT_FULL_DATE_SHORT_TIME.format(date);
					case SHORT_DATE_SHORT_TIME -> FORMAT_SHORT_DATE_SHORT_TIME.format(date);
					case SHORT_DATE_LONG_TIME -> FORMAT_SHORT_DATE_LONG_TIME.format(date);
					case RELATIVE -> TimeAgo.using(date.toInstant().toEpochMilli());
					default -> FORMAT_DEFAULT.format(date);
				};
			}

			public String getFull() {
				return FORMAT_DEFAULT.format(date);
			}

			public enum Format {
				DEFAULT,
				SHORT_TIME,
				LONG_TIME,
				SHORT_DATE,
				LONG_DATE,
				LONG_DATE_SHORT_TIME,
				FULL_DATE_SHORT_TIME,
				SHORT_DATE_SHORT_TIME,
				SHORT_DATE_LONG_TIME,
				RELATIVE
			}
		}
    }
}
