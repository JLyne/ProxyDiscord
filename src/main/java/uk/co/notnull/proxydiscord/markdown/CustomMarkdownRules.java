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

import dev.vankka.mcdiscordreserializer.rules.StyleNode;
import dev.vankka.simpleast.core.node.Node;
import dev.vankka.simpleast.core.parser.ParseSpec;
import dev.vankka.simpleast.core.parser.Parser;
import dev.vankka.simpleast.core.parser.Rule;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomMarkdownRules {
	private static final Pattern PATTERN_LINK = Pattern.compile("^<?https?://(?:(?:\\d{1,3}.){3}\\d{1,3}|[-\\w_.]+\\.\\w{2,})(?:[^\\s>]*)?>?");

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
}
