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
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.spongepowered.configurate.ConfigurationNode;
import org.javacord.api.entity.message.component.ActionRowBuilder;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Messages {
    private static ConfigurationNode messages;

    public static void set(ConfigurationNode messages) {
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

    public static @NotNull EmbedBuilder getEmbed(String id) {
        return getEmbed(id, Collections.emptyMap());
    }

    public static @NotNull EmbedBuilder getEmbed(String id, Map<String, String> replacements) {
        Set<Role> verifiedRoles = ProxyDiscord.inst().getVerificationManager().getVerifiedRoles();
        String roleLink;

        if(verifiedRoles.size() > 1) {
            roleLink = verifiedRoles.stream()
                    .map((Role role) -> "<@&" + role.getIdAsString() + ">").collect(Collectors.joining(", "));
        } else {
            roleLink = !verifiedRoles.isEmpty() ? "<@&" + verifiedRoles.iterator().next().getIdAsString() + ">" : "Unknown Role";
        }

        if(messages == null) {
            return new EmbedBuilder().setTitle("Failed to load messages configuration file");
        }

        if(!id.startsWith("embed")) {
            return new EmbedBuilder().setTitle("Invalid embed id " + id);
        }

        ConfigurationNode message = messages.node(id);

        if(message.virtual()) {
            return new EmbedBuilder().setTitle("Embed " + id + " does not exist");
        }

        Map<Object, ? extends ConfigurationNode> messageContent = message.childrenMap();
        EmbedBuilder embed = new EmbedBuilder();

        if(messageContent.containsKey("title")) {
            String title = messageContent.get("title").getString("").replace("<role>", roleLink);

            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                title = title.replace("<" + entry.getKey() + ">", entry.getValue());
            }

            embed.setTitle(title);
        }

        if(messageContent.containsKey("description")) {
            String description = messageContent.get("description").getString("").replace("<role>", roleLink);

            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                description = description.replace("<" + entry.getKey() + ">", entry.getValue());
            }

            embed.setDescription(description);
        }

        if(messageContent.containsKey("thumbnail")) {
            String thumbnail = messageContent.get("thumbnail").getString();

            if(thumbnail != null) {
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    thumbnail = thumbnail.replace("<" + entry.getKey() + ">", entry.getValue());
                }

                embed.setThumbnail(thumbnail);
            }
        }

        if(messageContent.containsKey("fields")) {
            List<? extends ConfigurationNode> fields = messageContent.get("fields").childrenList();

            for (ConfigurationNode field : fields) {
                String name = field.node("name").getString();
                String value = field.node("value").getString();

                if (name != null) {
                    for (Map.Entry<String, String> entry : replacements.entrySet()) {
                        name = name.replace("<" + entry.getKey() + ">", entry.getValue());
                    }
                }

                if (value != null) {
                    for (Map.Entry<String, String> entry : replacements.entrySet()) {
                        value = value.replace("<" + entry.getKey() + ">", entry.getValue());
                    }
                }

                embed.addField(name, value, field.node("inline").getBoolean(false));
            }
        }

        if(messageContent.containsKey("colour")) {
            Color color;

            try {
                color = Color.decode(message.node("colour").getString(""));
            } catch (NumberFormatException e) {
                color = Color.LIGHT_GRAY;
            }

            embed.setColor(color);
        }

        return embed;
    }

    public static @NotNull ActionRowBuilder getMessageActionRow(String id) {
        return getMessageActionRow(id, Collections.emptyMap());
    }

    public static @NotNull ActionRowBuilder getMessageActionRow(String id, Map<String, String> replacements) {
        if(messages == null) {
            return new ActionRowBuilder();
        }

        ConfigurationNode node = messages.node(id);

        if(node.virtual()) {
            return new ActionRowBuilder();
        }

        List<? extends ConfigurationNode> components = node.childrenList();
        ActionRowBuilder row = new ActionRowBuilder();

        for (ConfigurationNode component : components) {
            String label = component.node("label").getString();
            String url = component.node("url").getString();

            if (label != null) {
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    label = label.replace("<" + entry.getKey() + ">", entry.getValue());
                }
            }

            if (url != null) {
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    url = url.replace("<" + entry.getKey() + ">", entry.getValue());
                }
            }

            row.addComponents(Button.link(url, label));
        }

        return row;
    }

    public static void sendComponent(CommandSource recipient, String messageId) {
        recipient.sendMessage(getComponent(messageId));
    }

    public static void sendComponent(CommandSource recipient, String messageId, Map<String, String> stringReplacements, Map<String, ComponentLike> componentReplacmenets) {
        recipient.sendMessage(getComponent(messageId, stringReplacements, componentReplacmenets));
    }
}
