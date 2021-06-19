package uk.co.notnull.proxydiscord.bot.listeners;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.GroupSyncManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;
import org.javacord.api.listener.server.member.ServerMemberJoinListener;

public class ServerMemberJoin implements ServerMemberJoinListener {
	private final VerificationManager verificationManager;
	private final GroupSyncManager groupSyncManager;

	public ServerMemberJoin(ProxyDiscord plugin) {
		this.verificationManager = plugin.getVerificationManager();
		this.groupSyncManager = plugin.getGroupSyncManager();
	}

    @Override
    public void onServerMemberJoin(ServerMemberJoinEvent serverMemberJoinEvent) {
        verificationManager.handleServerMemberEvent(serverMemberJoinEvent);
        groupSyncManager.handleServerMemberEvent(serverMemberJoinEvent);
    }
}


