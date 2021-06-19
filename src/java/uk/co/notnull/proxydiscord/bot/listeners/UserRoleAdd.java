package uk.co.notnull.proxydiscord.bot.listeners;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.GroupSyncManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import org.javacord.api.event.server.role.UserRoleAddEvent;
import org.javacord.api.listener.server.role.UserRoleAddListener;

public class UserRoleAdd implements UserRoleAddListener {
    private final VerificationManager verificationManager;
    private final GroupSyncManager groupSyncManager;

    public UserRoleAdd(ProxyDiscord plugin) {
        this.verificationManager = plugin.getVerificationManager();
        this.groupSyncManager = plugin.getGroupSyncManager();
    }

    @Override
    public void onUserRoleAdd(UserRoleAddEvent userRoleAddEvent) {
        verificationManager.handleRoleEvent(userRoleAddEvent);
        groupSyncManager.handleRoleEvent(userRoleAddEvent);
    }
}


