package me.prouser123.bungee.discord.bot.commands.listeners;

import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.VerificationManager;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.member.ServerMemberBanEvent;
import org.javacord.api.listener.server.member.ServerMemberBanListener;

public class ServerMemberBan implements ServerMemberBanListener {

    public ServerMemberBan() {
    }

    @Override
    public void onServerMemberBan(ServerMemberBanEvent serverMemberBanEvent) {
        VerificationManager verificationManager = Main.inst().getVerificationManager();

        User user = serverMemberBanEvent.getUser();
        verificationManager.removeUser(user);
    }
}


