package uk.co.notnull.proxydiscord.bot.listeners;

import org.javacord.api.event.server.member.ServerMemberLeaveEvent;
import org.javacord.api.listener.server.member.ServerMemberLeaveListener;
import uk.co.notnull.proxydiscord.manager.VerificationManager;

public class ServerMemberLeave implements ServerMemberLeaveListener {
	private final VerificationManager verificationManager;

	public ServerMemberLeave(VerificationManager verificationManager) {
        this.verificationManager = verificationManager;
    }

    @Override
    public void onServerMemberLeave(ServerMemberLeaveEvent serverMemberLeaveEvent) {
		verificationManager.removeUser(serverMemberLeaveEvent.getUser());
    }
}


