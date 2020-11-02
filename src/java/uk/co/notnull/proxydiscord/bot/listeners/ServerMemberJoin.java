package uk.co.notnull.proxydiscord.bot.listeners;

import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.VerificationManager;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;
import org.javacord.api.listener.server.member.ServerMemberJoinListener;

import java.util.Optional;

public class ServerMemberJoin implements ServerMemberJoinListener {

	private final VerificationManager verificationManager;

	public ServerMemberJoin() {
        verificationManager = ProxyDiscord.inst().getVerificationManager();
    }

    @Override
    public void onServerMemberJoin(ServerMemberJoinEvent serverMemberJoinEvent) {
        User user = serverMemberJoinEvent.getUser();
        Server server = serverMemberJoinEvent.getServer();
        Optional<Role> role = server.getRoleById(verificationManager.getVerifiedRoleId());

        if(role.isPresent() && server.getRoles(user).contains(role.get())) {
        	verificationManager.addUser(user);
		} else {
        	verificationManager.removeUser(user);
		}
    }
}


