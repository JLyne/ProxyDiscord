package me.prouser123.bungee.discord.bot.commands.listeners;

import me.prouser123.bungee.discord.ProxyDiscord;
import me.prouser123.bungee.discord.VerificationManager;
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


