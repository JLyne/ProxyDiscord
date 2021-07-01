package uk.co.notnull.proxydiscord.api.logging;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("unused")
public class LogEntry {
	private final LogType type;
	private final LogVisibility visibility;
	private final Player player;
	private final RegisteredServer server;
	private final Map<String, String> replacements;

	public LogEntry(Builder builder) {
		this.type = builder.type;
		this.visibility = builder.visibility;
		this.player = builder.player;
		this.server = builder.server;
		this.replacements = builder.replacements;
	}

	public LogType getType() {
		return type;
	}

	public LogVisibility getVisibility() {
		return visibility;
	}

	public Player getPlayer() {
		return player;
	}

	public Optional<RegisteredServer> getServer() {
		if(server != null) {
			return Optional.of(server);
		}

		return player.getCurrentServer().map(ServerConnection::getServer);
	}

	public Map<String, String> getReplacements() {
		return replacements;
	}

	@NonNull
	public Builder toBuilder() {
		return new Builder(this);
	}

	@NonNull
	public static Builder builder() {
		return new Builder();
	}

	@NonNull
	public static Builder builder(Builder builder) {
		return new Builder(builder);
	}

	public static class Builder {
		private LogType type;
		private LogVisibility visibility = LogVisibility.UNSPECIFIED;
		private Player player;
		private RegisteredServer server = null;
		private Map<String, String> replacements = Collections.emptyMap();

		public Builder() {
		}

		public Builder(Builder builder) {
			this.type = builder.type;
			this.visibility = builder.visibility;
			this.player = builder.player;
			this.server = builder.server;
			this.replacements = builder.replacements;
		}

		public Builder(LogEntry entry) {
			this.type = entry.getType();
			this.visibility = entry.getVisibility();
			this.player = entry.getPlayer();
			this.server = entry.getServer().orElse(null);
			this.replacements = entry.getReplacements();
		}

		public Builder visibility(@NonNull LogVisibility visibility) {
			this.visibility = Objects.requireNonNull(visibility);
			return this;
		}

		public Builder player(@NonNull Player player) {
			this.player = Objects.requireNonNull(player);
			return this;
		}

		public Builder server(RegisteredServer server) {
			this.server = server;
			return this;
		}

		public Builder type(LogType type) {
			this.type = type;
			return this;
		}

		public Builder replacements(@NonNull Map<String, String> replacements) {
			this.replacements = Objects.requireNonNull(replacements);
			return this;
		}

		public LogEntry build() {
			if(type == null) {
				throw new IllegalStateException("");
			}

			if(player == null) {
				throw new IllegalStateException("");
			}

			return new LogEntry(this);
		}
	}
}