package uk.co.notnull.proxydiscord.bot.listeners;

import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;
import org.javacord.api.listener.server.member.ServerMemberJoinListener;

import java.util.Collections;
import java.util.Set;

public class ServerMemberJoin implements ServerMemberJoinListener {
	private final VerificationManager verificationManager;

	public ServerMemberJoin(ProxyDiscord plugin) {
		this.verificationManager = plugin.getVerificationManager();
	}

    @Override
    public void onServerMemberJoin(ServerMemberJoinEvent serverMemberJoinEvent) {
        User user = serverMemberJoinEvent.getUser();
        Server server = serverMemberJoinEvent.getServer();
        Set<Role> roles = verificationManager.getVerifiedRoles();

        if(roles.isEmpty()) {
        	return;
		}

        if(!Collections.disjoint(server.getRoles(user), roles)) {
        	verificationManager.addUser(user);
		} else {
        	verificationManager.removeUser(user);
		}
    }
}


