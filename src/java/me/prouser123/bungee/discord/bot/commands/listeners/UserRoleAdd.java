package me.prouser123.bungee.discord.bot.commands.listeners;

import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.VerificationManager;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleAddEvent;
import org.javacord.api.listener.server.role.UserRoleAddListener;

public class UserRoleAdd implements UserRoleAddListener {
    private static VerificationManager verificationManager;
    private Role verifiedRole;

    public UserRoleAdd(Role verifiedRole) {
        this.verifiedRole = verifiedRole;
        verificationManager = Main.inst().getVerificationManager();
    }

    @Override
    public void onUserRoleAdd(UserRoleAddEvent userRoleAddEvent) {
        if (userRoleAddEvent.getRole().equals(verifiedRole)) {
            User user = userRoleAddEvent.getUser();

            verificationManager.addUser(user);
        }
    }
}


