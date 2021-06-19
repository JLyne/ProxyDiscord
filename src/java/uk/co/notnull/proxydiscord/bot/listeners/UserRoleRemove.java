package uk.co.notnull.proxydiscord.bot.listeners;

import org.javacord.api.entity.permission.Role;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleRemoveEvent;
import org.javacord.api.listener.server.role.UserRoleRemoveListener;

import java.util.Collections;
import java.util.List;

public class UserRoleRemove implements UserRoleRemoveListener {
    private final VerificationManager verificationManager;

    public UserRoleRemove(ProxyDiscord plugin) {
        this.verificationManager = plugin.getVerificationManager();
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent userRoleRemoveEvent) {
        verificationManager.handleRoleEvent(userRoleRemoveEvent);
    }
}


