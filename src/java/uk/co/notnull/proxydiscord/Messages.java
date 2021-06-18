package uk.co.notnull.proxydiscord;

import ninja.leaping.configurate.ConfigurationNode;
import org.javacord.api.entity.Nameable;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Messages {
    private static ConfigurationNode messages;

    public static void setMessages(ConfigurationNode messages) {
        Messages.messages = messages;
    }

    public static String getMessage(String id) {
        return getMessage(id, Collections.emptyMap());
    }

    public static String getMessage(String id, Map<String, String> replacements) {
        Set<Role> verifiedRoles = ProxyDiscord.inst().getVerificationManager().getVerifiedRoles();

        if(messages == null) {
            return "";
        }

        String message = messages.getNode(id).getString("Message " + id + " does not exist");
        String roleNames;

        if(verifiedRoles.size() > 1) {
            roleNames = verifiedRoles.stream().map(Nameable::getName).collect(Collectors.joining());
        } else {
            roleNames = !verifiedRoles.isEmpty() ? verifiedRoles.iterator().next().getName() : "Unknown Role";
        }

        message = message.replace("[role]", roleNames);

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        return message;
    }

    public static EmbedBuilder getEmbed(String id) {
        return getEmbed(id, Collections.emptyMap());
    }

    public static EmbedBuilder getEmbed(String id, Map<String, String> replacements) {
        Set<Role> verifiedRoles = ProxyDiscord.inst().getVerificationManager().getVerifiedRoles();
        String roleLink;

        if(verifiedRoles.size() > 1) {
            roleLink = verifiedRoles.stream()
                    .map((Role role) -> "<@&" + role.getIdAsString() + ">").collect(Collectors.joining(", "));
        } else {
            roleLink = !verifiedRoles.isEmpty() ? "<@&" + verifiedRoles.iterator().next().getIdAsString() + ">" : "Unknown Role";
        }

        if(messages == null || !id.startsWith("embed")) {
            return null;
        }

        ConfigurationNode message = messages.getNode(id);

        if(message.isVirtual()) {
            return null;
        }

        Map<Object, ? extends ConfigurationNode> messageContent = message.getChildrenMap();
        EmbedBuilder embed = new EmbedBuilder();

        if(messageContent.containsKey("title")) {
            String title = messageContent.get("title").getString("").replace("[role]", roleLink);

            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                title = title.replace(entry.getKey(), entry.getValue());
            }

            embed.setTitle(title);
        }

        if(messageContent.containsKey("description")) {
            String description = messageContent.get("description").getString("").replace("[role]", roleLink);

            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                description = description.replace(entry.getKey(), entry.getValue());
            }

            embed.setDescription(description);
        }

        if(messageContent.containsKey("thumbnail")) {
            embed.setThumbnail(messageContent.get("thumbnail").getString());
        }

        if(messageContent.containsKey("fields")) {
            List<? extends ConfigurationNode> fields = messageContent.get("fields").getChildrenList();

            for(int i = 0; i < fields.size(); i += 2) {
                String name = fields.get(i).getString();
                String value = "";

                if(i + 1 < fields.size()) {
                    value = fields.get(i + 1).getString();
                }

                if(name != null) {
                    for (Map.Entry<String, String> entry : replacements.entrySet()) {
                        name = name.replace(entry.getKey(), entry.getValue());
                    }
                }

                if(value != null) {
                    for (Map.Entry<String, String> entry : replacements.entrySet()) {
                        value = value.replace(entry.getKey(), entry.getValue());
                    }
                }

                embed.addField(name, value);
            }
        }

        if(messageContent.containsKey("colour")) {
            Color color;

            try {
                color = Color.decode(message.getNode("colour").getString(""));
            } catch (NumberFormatException e) {
                color = Color.LIGHT_GRAY;
            }

            embed.setColor(color);
        }

        return embed;
    }
}