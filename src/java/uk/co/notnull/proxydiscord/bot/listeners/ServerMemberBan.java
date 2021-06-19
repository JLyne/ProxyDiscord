package uk.co.notnull.proxydiscord.bot.listeners;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.member.ServerMemberBanEvent;
import org.javacord.api.listener.server.member.ServerMemberBanListener;

public class ServerMemberBan implements ServerMemberBanListener {
    private final VerificationManager verificationManager;

    public ServerMemberBan(ProxyDiscord plugin) {
        this.verificationManager = plugin.getVerificationManager();
    }

    @Override
    public void onServerMemberBan(ServerMemberBanEvent serverMemberBanEvent) {
        verificationManager.handleServerMemberEvent(serverMemberBanEvent);
    }
}


