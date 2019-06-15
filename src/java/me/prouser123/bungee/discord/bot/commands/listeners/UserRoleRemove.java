package me.prouser123.bungee.discord.bot.commands.listeners;

import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.VerificationManager;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleRemoveEvent;
import org.javacord.api.listener.server.role.UserRoleRemoveListener;

public class UserRoleRemove implements UserRoleRemoveListener {
    private static VerificationManager verificationManager;
    private Role verifiedRole;

    public UserRoleRemove(Role verifiedRole) {
        this.verifiedRole = verifiedRole;
        verificationManager = Main.inst().getVerificationManager();
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent userRoleRemoveEvent) {
        if (userRoleRemoveEvent.getRole().equals(verifiedRole)) {
            User user = userRoleRemoveEvent.getUser();

            verificationManager.removeUser(user);
        }
    }
}


