package uk.co.notnull.proxydiscord.bot.listeners;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.VerificationManager;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleRemoveEvent;
import org.javacord.api.listener.server.role.UserRoleRemoveListener;

public class UserRoleRemove implements UserRoleRemoveListener {

    public UserRoleRemove() {
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent userRoleRemoveEvent) {
        VerificationManager verificationManager = ProxyDiscord.inst().getVerificationManager();

        if (userRoleRemoveEvent.getRole().getIdAsString().equals(verificationManager.getVerifiedRoleId())) {
            User user = userRoleRemoveEvent.getUser();

            verificationManager.removeUser(user);
        }
    }
}


