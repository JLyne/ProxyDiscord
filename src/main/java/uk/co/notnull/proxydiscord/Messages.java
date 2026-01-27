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

import com.velocitypowered.api.command.CommandSource;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.components.utils.ComponentDeserializer;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ParsingException;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.spongepowered.configurate.ConfigurationNode;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Messages {
    private static ConfigurationNode messages;

    private static final Map<String, MessageComponentTree> componentCache = new ConcurrentHashMap<>();

    public static void set(ConfigurationNode messages) {
        componentCache.clear();
        Messages.messages = messages;
    }

    public static @NotNull String get(String id) {
        return get(id, Collections.emptyMap());
    }

    public static @NotNull String get(String id, Map<String, String> replacements) {
        if(messages == null) {
            return "";
        }

        String message = messages.node((Object[]) id.split("\\."))
                .getString("Message " + id + " does not exist");

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        return message;
    }

    public static @NotNull Component getComponent(String id) {
        return getComponent(id, Collections.emptyMap(), Collections.emptyMap());
    }

    public static @NotNull Component getComponent(String id, Map<String, String> stringReplacements, Map<String, ComponentLike> componentReplacmenets) {
        if(messages == null) {
            return Component.empty();
        }

        String message = messages.node((Object[]) id.split("\\."))
                .getString("Message " + id + " does not exist");

        TagResolver.@NotNull Builder placeholders = TagResolver.builder();

        for (Map.Entry<String, String> entry : stringReplacements.entrySet()) {
            placeholders.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }

        for (Map.Entry<String, ComponentLike> entry : componentReplacmenets.entrySet()) {
            placeholders.resolver(Placeholder.component(entry.getKey(), entry.getValue()));
        }

        return Util.miniMessage.deserialize(message, placeholders.build());
    }

    public static @NotNull MessageComponentTree getMessageComponents(String id) {
        return getMessageComponents(id, Collections.emptyMap());
    }

    public static MessageComponentTree getMessageComponents(String id, Map<String, String> replacements) {
        Set<Role> verifiedRoles = ProxyDiscord.inst().getVerificationManager().getVerifiedRoles();
        String roleLink;

        if(verifiedRoles.size() > 1) {
            roleLink = verifiedRoles.stream().map(Role::getAsMention).collect(Collectors.joining(", "));
        } else {
            roleLink = !verifiedRoles.isEmpty() ? verifiedRoles.iterator().next().getAsMention() : "Unknown Role";
        }

        MessageComponentTree message = componentCache.computeIfAbsent(id, _ -> {
            if(messages == null) {
                return MessageComponentTree.of(TextDisplay.of("Failed to load messages configuration file"));
            }

            if(!id.startsWith("discord-")) {
                return MessageComponentTree.of(TextDisplay.of("Invalid message id " + id));
            }

            ConfigurationNode node = messages.node(id);

            if(node.virtual()) {
                return MessageComponentTree.of(TextDisplay.of("Message " + id + " does not exist"));
            }

            try {
                return new ComponentDeserializer(Collections.emptyList())
                        .deserializeAsTree(MessageComponentTree.class, DataArray.fromJson(node.getString("")));
            } catch (ParsingException e) {
                ProxyDiscord.inst().getLogger().warn("Exception while parsing message {}", id, e);
                return MessageComponentTree.of(TextDisplay.of("Failed to parse message " + id));
            }
        });


        Function<String, String> replace = s -> {
            if (s == null) {
                return null;
            }

            s = s.replace("<role>", roleLink);

            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                s = s.replace("<" + entry.getKey() + ">", entry.getValue());
            }

            return s;
        };

        ComponentReplacer componentReplacer = (net.dv8tion.jda.api.components.Component oldComponent) -> {
            switch (oldComponent) {
                case TextDisplay td -> {
                    return td.withContent(replace.apply(td.getContent()));
                }
                case Thumbnail t -> {
                    return Thumbnail.fromUrl(replace.apply(t.getUrl()))
                            .withDescription(replace.apply(t.getDescription()));
                }
                case Button b -> {
                    return b.withLabel(replace.apply(b.getLabel())).withUrl(replace.apply(b.getUrl()));
                }
                default -> {
                    return oldComponent;
                }
            }
        };

        return message.replace(componentReplacer);
    }

    public static void sendComponent(CommandSource recipient, String messageId) {
        recipient.sendMessage(getComponent(messageId));
    }

    public static void sendComponent(CommandSource recipient, String messageId, Map<String, String> stringReplacements, Map<String, ComponentLike> componentReplacmenets) {
        recipient.sendMessage(getComponent(messageId, stringReplacements, componentReplacmenets));
    }
}
