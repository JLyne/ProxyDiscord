package uk.co.notnull.proxydiscord.bot.listeners;

import org.javacord.api.entity.permission.Role;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleRemoveEvent;
import org.javacord.api.listener.server.role.UserRoleRemoveListener;

import java.util.Collections;
import java.util.List;

public class UserRoleRemove implements UserRoleRemoveListener {
    private final VerificationManager verificationManager;

    public UserRoleRemove(VerificationManager verificationManager) {
        this.verificationManager = verificationManager;
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent userRoleRemoveEvent) {

        if(!verificationManager.getVerifiedRoleIds().contains(userRoleRemoveEvent.getRole().getIdAsString())) {
            return;
        }

        User user = userRoleRemoveEvent.getUser();
        List<Role> roles = user.getRoles(userRoleRemoveEvent.getServer());

        if (Collections.disjoint(roles, verificationManager.getVerifiedRoles())) {
            verificationManager.removeUser(user);
        }
    }
}


