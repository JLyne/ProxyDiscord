package uk.co.notnull.proxydiscord.bot.listeners;

import uk.co.notnull.proxydiscord.manager.VerificationManager;
import org.javacord.api.event.connection.ReconnectEvent;
import org.javacord.api.listener.connection.ReconnectListener;

public class Reconnect implements ReconnectListener {
    private final VerificationManager verificationManager;

    public Reconnect(VerificationManager verificationManager) {
        this.verificationManager = verificationManager;
    }

    @Override
    public void onReconnect(ReconnectEvent reconnectEvent) {
        if(!verificationManager.getVerifiedRoleIds().isEmpty()) {
            verificationManager.populateUsers();
        }
    }
}


