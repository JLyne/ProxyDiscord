package me.prouser123.bungee.discord.bot.commands.listeners;

import me.prouser123.bungee.discord.Main;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleAddEvent;
import org.javacord.api.listener.server.role.UserRoleAddListener;

public class UserRoleAdd implements UserRoleAddListener {
    private Role verifiedRole;

    public UserRoleAdd(Role verifiedRole) {
        this.verifiedRole = verifiedRole;
    }

    @Override
    public void onUserRoleAdd(UserRoleAddEvent userRoleAddEvent) {
        if (userRoleAddEvent.getRole().equals(verifiedRole)) {
            User user = userRoleAddEvent.getUser();

            Main.inst().getVerificationManager().addUser(user);
        }
    }
}


