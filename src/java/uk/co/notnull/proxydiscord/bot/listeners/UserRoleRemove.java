package uk.co.notnull.proxydiscord.bot.listeners;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.GroupSyncManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import org.javacord.api.event.server.role.UserRoleRemoveEvent;
import org.javacord.api.listener.server.role.UserRoleRemoveListener;

public class UserRoleRemove implements UserRoleRemoveListener {
    private final VerificationManager verificationManager;
    private final GroupSyncManager groupSyncManager;

    public UserRoleRemove(ProxyDiscord plugin) {
        this.verificationManager = plugin.getVerificationManager();
        this.groupSyncManager = plugin.getGroupSyncManager();
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent userRoleRemoveEvent) {
        verificationManager.handleRoleEvent(userRoleRemoveEvent);
        groupSyncManager.handleRoleEvent(userRoleRemoveEvent);
    }
}


