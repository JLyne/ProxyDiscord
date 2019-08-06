package me.prouser123.bungee.discord;

import net.md_5.bungee.config.Configuration;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;

import java.awt.*;
import java.util.List;

public class ChatMessages {
    private static Configuration messages;

    ChatMessages(Configuration messages) {
        ChatMessages.messages = messages;
    }

    public static String getMessage(String id) {
        Role verifiedRole = Main.inst().getVerificationManager().getVerifiedRole();
        String message = messages.getString(id);

        if(verifiedRole != null) {
            message = message.replace("[role]", verifiedRole.getName());
        }

        return message;
    }

    public static EmbedBuilder getEmbed(String id) {
        Role verifiedRole = Main.inst().getVerificationManager().getVerifiedRole();
        String roleLink = verifiedRole != null ? "<@&" + verifiedRole.getIdAsString() + ">" : "Unknown Role";

        if(!id.startsWith("embed")) {
            return null;
        }

        Configuration message = messages.getSection(id);

        if(message == null) {
            return null;
        }

        EmbedBuilder embed = new EmbedBuilder();

        if(message.contains("title")) {
            embed.setTitle(message.getString("title").replace("[role]", roleLink));
        }

        if(message.contains("description")) {
            embed.setDescription(message.getString("description").replace("[role]", roleLink));
        }

        if(message.contains("thumbnail")) {
            embed.setThumbnail(message.getString("thumbnail"));
        }

        if(message.contains("fields")) {
            List <String> fields = message.getStringList("fields");

            for(int i = 0; i < fields.size(); i += 2) {
                String name = fields.get(i);
                String value = "";

                if(i + 1 < fields.size()) {
                    value = fields.get(i + 1);
                }

                embed.addField(name, value);
            }
        }

        if(message.contains("colour")) {
            Color color;

            try {
                color = Color.decode(message.getString("colour"));
            } catch (NumberFormatException e) {
                color = Color.LIGHT_GRAY;
            }

            embed.setColor(color);
        }

        return embed;
    }
}
