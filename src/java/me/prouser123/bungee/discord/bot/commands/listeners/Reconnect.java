package me.prouser123.bungee.discord.bot.commands.listeners;

import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.VerificationManager;
import org.javacord.api.event.connection.ReconnectEvent;
import org.javacord.api.listener.connection.ReconnectListener;

public class Reconnect implements ReconnectListener {
    public Reconnect() {
    }

    @Override
    public void onReconnect(ReconnectEvent reconnectEvent) {
        VerificationManager verificationManager = Main.inst().getVerificationManager();

        verificationManager.populateUsers();
    }
}


