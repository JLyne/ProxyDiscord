package uk.co.notnull.proxydiscord.bot.commands.listeners;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.VerificationManager;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleAddEvent;
import org.javacord.api.listener.server.role.UserRoleAddListener;

public class UserRoleAdd implements UserRoleAddListener {
    public UserRoleAdd() {
    }

    @Override
    public void onUserRoleAdd(UserRoleAddEvent userRoleAddEvent) {
        VerificationManager verificationManager = ProxyDiscord.inst().getVerificationManager();

        if(userRoleAddEvent.getRole().getIdAsString().equals(verificationManager.getVerifiedRoleId())) {
            User user = userRoleAddEvent.getUser();

            verificationManager.addUser(user);
        }
    }
}


