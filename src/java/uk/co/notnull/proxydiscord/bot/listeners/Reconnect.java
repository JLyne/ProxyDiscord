package uk.co.notnull.proxydiscord.bot.listeners;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.VerificationManager;
import org.javacord.api.event.connection.ReconnectEvent;
import org.javacord.api.listener.connection.ReconnectListener;

public class Reconnect implements ReconnectListener {
    public Reconnect() {
    }

    @Override
    public void onReconnect(ReconnectEvent reconnectEvent) {
        VerificationManager verificationManager = ProxyDiscord.inst().getVerificationManager();

        if(!verificationManager.getVerifiedRoleIds().isEmpty()) {
            verificationManager.populateUsers();
        }
    }
}


