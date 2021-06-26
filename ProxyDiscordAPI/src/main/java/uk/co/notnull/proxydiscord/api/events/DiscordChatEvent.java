package uk.co.notnull.proxydiscord.api.events;

import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.luckperms.api.model.user.User;

import java.util.Set;

@SuppressWarnings("unused")
public final class DiscordChatEvent {
	private final User user;
	private final Set<RegisteredServer> servers;
	private final String message;
	private PlayerChatEvent.ChatResult result = PlayerChatEvent.ChatResult.allowed();

	public DiscordChatEvent(User user, String message, Set<RegisteredServer> servers) {
		this.user = user;
		this.message = message;
		this.servers = servers;
	}

	public User getUser() {
		return user;
	}

	public String getMessage() {
		return message;
	}

	public PlayerChatEvent.ChatResult getResult() {
		return result;
	}

	public void setResult(PlayerChatEvent.ChatResult result) {
		this.result = result;
	}

	public Set<RegisteredServer> getServers() {
		return servers;
	}

	@Override
	public String toString() {
		return "PlayerChatEvent{"
				+ "user=" + user
				+ ", message=" + message
				+ ", result=" + result
				+ '}';
	}
}
