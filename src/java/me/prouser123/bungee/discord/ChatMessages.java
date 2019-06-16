package me.prouser123.bungee.discord;

import net.md_5.bungee.config.Configuration;

public class ChatMessages {
    private static Configuration messages;

    ChatMessages(Configuration messages) {
        ChatMessages.messages = messages;
    }

    public static String getMessage(String id) {
        String message = messages.getString(id);

        return message.replace("[role]", VerificationManager.verifiedRole.getName());
    }
}
