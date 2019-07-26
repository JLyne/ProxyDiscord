package me.prouser123.bungee.discord;

import net.md_5.bungee.config.Configuration;
import org.javacord.api.entity.permission.Role;

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
}