/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2021 James Lyne
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package uk.co.notnull.proxydiscord.api.logging;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A loggable event which will be sent to configured Discord logging channels
 */
@SuppressWarnings("unused")
public class LogEntry {
	private final LogType type;
	private final LogVisibility visibility;
	private final Player player;
	private final RegisteredServer server;
	private final Map<String, String> replacements;

	/**
	 * Constructs a LogEntry.
	 * @param builder A LogEntry builder
	 */
	public LogEntry(Builder builder) {
		this.type = builder.type;
		this.visibility = builder.visibility;
		this.player = builder.player;
		this.server = builder.server;
		this.replacements = builder.replacements;
	}

	/**
	 * Gets the {@link LogType} of the entry
	 * @return the log type
	 */
	public LogType getType() {
		return type;
	}

	/**
	 * Gets the {@link LogVisibility} of the entry
	 * @return the log visibility
	 */
	public LogVisibility getVisibility() {
		return visibility;
	}

	/**
	 * Gets the player associated with the entry
	 * @return the player
	 */
	public Player getPlayer() {
		return player;
	}

	/**
	 * Gets the server associated with the entry
	 * @return the server
	 */
	public Optional<RegisteredServer> getServer() {
		if(server != null) {
			return Optional.of(server);
		}

		return player.getCurrentServer().map(ServerConnection::getServer);
	}

	/**
	 * Gets the list of replacements to apply to the log entry format before logging
	 * @return the replacements
	 */
	public Map<String, String> getReplacements() {
		return replacements;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LogEntry logEntry = (LogEntry) o;
		return getType() == logEntry.getType() && getVisibility() == logEntry.getVisibility() && getPlayer().equals(
				logEntry.getPlayer()) && Objects.equals(getServer(),
														logEntry.getServer()) && Objects.equals(
				getReplacements(), logEntry.getReplacements());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getType(), getVisibility(), getPlayer(), getServer(), getReplacements());
	}

	@Override
	public String toString() {
		return "LogEntry{" +
				"type=" + type +
				", visibility=" + visibility +
				", player=" + player +
				", server=" + server +
				", replacements=" + replacements +
				'}';
	}

	/**
	 * Create a {@link Builder} from the log entry
	 * @return the builder
	 */
	@NonNull
	public Builder toBuilder() {
		return new Builder(this);
	}

	/**
	 * Create a new empty {@link Builder}
	 * @return the builder
	 */
	@NonNull
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a copy of an existing {@link Builder}
	 * @return the new builder
	 */
	@NonNull
	public static Builder builder(Builder builder) {
		return new Builder(builder);
	}

	/**
	 * Builder for creating {@link LogEntry} objects
	 */
	public static class Builder {
		private LogType type;
		private LogVisibility visibility = LogVisibility.UNSPECIFIED;
		private Player player;
		private RegisteredServer server = null;
		private Map<String, String> replacements = Collections.emptyMap();

		/**
		 * Constructs an empty Builder.
		 */
		public Builder() {
		}

		/**
		 * Constructs a Builder from another Builder instance.
		 * @param builder An existing builder to copy the settings from
		 */
		public Builder(Builder builder) {
			this.type = builder.type;
			this.visibility = builder.visibility;
			this.player = builder.player;
			this.server = builder.server;
			this.replacements = builder.replacements;
		}

		/**
		 * Constructs a Builder from an existing {@link LogEntry}
		 * @param entry An existing {@link LogEntry}} to copy the properties from
		 */
		public Builder(LogEntry entry) {
			this.type = entry.getType();
			this.visibility = entry.getVisibility();
			this.player = entry.getPlayer();
			this.server = entry.getServer().orElse(null);
			this.replacements = entry.getReplacements();
		}

		/**
		 * Set the {@link LogVisibility} for the final log entry
		 * @return the builder
		 */
		public Builder visibility(@NonNull LogVisibility visibility) {
			this.visibility = Objects.requireNonNull(visibility);
			return this;
		}

		/**
		 * Set the associated player for the final log entry
		 * @return the builder
		 */
		public Builder player(@NonNull Player player) {
			this.player = Objects.requireNonNull(player);
			return this;
		}

		/**
		 * Set the associated server for the final log entry
		 * @return the builder
		 */
		public Builder server(RegisteredServer server) {
			this.server = server;
			return this;
		}

		/**
		 * Set the log type for the final log entry
		 * @return the builder
		 */
		public Builder type(LogType type) {
			this.type = type;
			return this;
		}

		/**
		 * Set the format replacements fro the final log entry
		 * @return the builder
		 */
		public Builder replacements(@NonNull Map<String, String> replacements) {
			this.replacements = Objects.requireNonNull(replacements);
			return this;
		}

		/**
		 * Build the final log entry
		 * @return the log entry
		 */
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
