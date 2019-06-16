package me.prouser123.bungee.discord.bot.commands.listeners;

import me.prouser123.bungee.discord.Main;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleRemoveEvent;
import org.javacord.api.listener.server.role.UserRoleRemoveListener;

public class UserRoleRemove implements UserRoleRemoveListener {
    private Role verifiedRole;

    public UserRoleRemove(Role verifiedRole) {
        this.verifiedRole = verifiedRole;
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent userRoleRemoveEvent) {
        if (userRoleRemoveEvent.getRole().equals(verifiedRole)) {
            User user = userRoleRemoveEvent.getUser();

            Main.inst().getVerificationManager().removeUser(user);
        }
    }
}


