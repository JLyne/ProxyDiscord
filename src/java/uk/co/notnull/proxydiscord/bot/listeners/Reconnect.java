package uk.co.notnull.proxydiscord.bot.listeners;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.GroupSyncManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import org.javacord.api.event.connection.ReconnectEvent;
import org.javacord.api.listener.connection.ReconnectListener;

public class Reconnect implements ReconnectListener {
    private final VerificationManager verificationManager;
    private final GroupSyncManager groupSyncManager;

    public Reconnect(ProxyDiscord plugin) {
        this.verificationManager = plugin.getVerificationManager();
        this.groupSyncManager = plugin.getGroupSyncManager();
    }

    @Override
    public void onReconnect(ReconnectEvent reconnectEvent) {
        verificationManager.populateUsers();
        groupSyncManager.populateUsers();
    }
}


