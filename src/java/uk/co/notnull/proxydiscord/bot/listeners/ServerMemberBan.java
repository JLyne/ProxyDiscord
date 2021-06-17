package uk.co.notnull.proxydiscord.bot.listeners;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.member.ServerMemberBanEvent;
import org.javacord.api.listener.server.member.ServerMemberBanListener;

public class ServerMemberBan implements ServerMemberBanListener {

    public ServerMemberBan() {
    }

    @Override
    public void onServerMemberBan(ServerMemberBanEvent serverMemberBanEvent) {
        VerificationManager verificationManager = ProxyDiscord.inst().getVerificationManager();

        User user = serverMemberBanEvent.getUser();
        verificationManager.removeUser(user);
    }
}


