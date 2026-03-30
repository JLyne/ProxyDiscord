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

package uk.co.notnull.proxydiscord.markdown;

import dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializerOptions;
import dev.vankka.mcdiscordreserializer.renderer.implementation.DefaultMinecraftRenderer;
import dev.vankka.mcdiscordreserializer.rules.StyleNode;
import dev.vankka.simpleast.core.node.Node;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import uk.co.notnull.proxydiscord.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class CustomMinecraftRenderer extends DefaultMinecraftRenderer {
	@Override
    public Component render(@NotNull Component component,
                             @NotNull Node<Object> node,
                             @NotNull MinecraftSerializerOptions<Component> serializerOptions,
                             @NotNull Function<Node<Object>, Component> renderWithChildren) {
        if (node instanceof StyleNode) {
            List<StyleNode.Style> styles = new ArrayList<>(((StyleNode<?, StyleNode.Style>) node).getStyles());
            for (StyleNode.Style style : styles) {
				if (style instanceof CustomMarkdownRules.TimestampStyle timestampStyle) {
					component = appendTimestamp(component, timestampStyle.getTimestamp());
					((StyleNode<?, StyleNode.Style>) node).getStyles().remove(style);
				} else {
					component = super.render(component, node, serializerOptions, renderWithChildren);
				}
			}
        } else {
			component = super.render(component, node, serializerOptions, renderWithChildren);
		}

        return component;
    }


	@Override
    public Component link(@NotNull Component part, String link) {
        return part.clickEvent(ClickEvent.openUrl(link))
				.decoration(TextDecoration.UNDERLINED, TextDecoration.State.TRUE)
				.color(TextColor.fromHexString("#8194e4"));
    }

	@Override
    public @NotNull Component appendSpoiler(@NotNull Component component, @NotNull Component content) {
         return component.append(
				 Component.text().content("████████").color(NamedTextColor.BLACK)
						 .hoverEvent(HoverEvent.showText(content)));
    }

    public @NotNull Component appendTimestamp(@NotNull Component component, @NotNull CustomMarkdownRules.TimestampStyle.Timestamp timestamp) {
         return component.append(
				 Component.text().content(timestamp.getFormatted()).color(NamedTextColor.GRAY)
						 .hoverEvent(
								 HoverEvent.showText(
										 Messages.getComponent("timestamp-hover",
															   Collections.singletonMap("time", timestamp.getFull()),
															   Collections.emptyMap()))));

    }

	@Override
    @NotNull
    public Component codeString(@NotNull Component component) {
        return component.color(NamedTextColor.GRAY);
    }

    @Override
    @NotNull
    public Component codeBlock(@NotNull Component component) {
        return component.color(NamedTextColor.GRAY);
    }
}
