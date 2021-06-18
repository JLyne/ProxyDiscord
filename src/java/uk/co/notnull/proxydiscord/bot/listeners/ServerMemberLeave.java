package uk.co.notnull.proxydiscord.bot.listeners;

import org.javacord.api.event.server.member.ServerMemberLeaveEvent;
import org.javacord.api.listener.server.member.ServerMemberLeaveListener;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;

public class ServerMemberLeave implements ServerMemberLeaveListener {
	private final VerificationManager verificationManager;

	public ServerMemberLeave(ProxyDiscord plugin) {
		this.verificationManager = plugin.getVerificationManager();
	}

    @Override
    public void onServerMemberLeave(ServerMemberLeaveEvent serverMemberLeaveEvent) {
		verificationManager.removeUser(serverMemberLeaveEvent.getUser());
    }
}


