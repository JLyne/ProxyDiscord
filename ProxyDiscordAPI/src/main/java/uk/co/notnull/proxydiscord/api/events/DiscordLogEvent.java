package uk.co.notnull.proxydiscord.api.events;

import com.velocitypowered.api.event.ResultedEvent;
import uk.co.notnull.proxydiscord.api.logging.LogEntry;
import uk.co.notnull.proxydiscord.api.logging.LogVisibility;

import java.util.Objects;

@SuppressWarnings("unused")
public class DiscordLogEvent implements ResultedEvent<DiscordLogEvent.DiscordLogResult> {
	private final LogEntry event;
	private DiscordLogResult result;

	public DiscordLogEvent(LogEntry event) {
		this.event = event;
		this.result = new DiscordLogResult(true, event.getVisibility());
	}

	public LogEntry getLogEntry() {
		return event;
	}

	@Override
	public DiscordLogResult getResult() {
		return result;
	}

	@Override
	public void setResult(DiscordLogResult result) {
		this.result = Objects.requireNonNull(result);
	}

	public static class DiscordLogResult implements ResultedEvent.Result {

		private static final DiscordLogResult ALLOWED = new DiscordLogResult(true, LogVisibility.UNSPECIFIED);
		private static final DiscordLogResult DENIED = new DiscordLogResult(false, LogVisibility.UNSPECIFIED);

		private final LogVisibility visibility;
		private final boolean status;

		private DiscordLogResult(boolean status, LogVisibility visibility) {
			this.status = status;
			this.visibility = visibility;
		}

		public LogVisibility getVisibility() {
			return visibility;
		}

		@Override
		public boolean isAllowed() {
			return status;
		}

		@Override
		public String toString() {
			return status ? "allowed " + visibility : "denied " + visibility;
		}

		/**
		 * Allows the log to be sent, without modification.
		 *
		 * @return the allowed result
		 */
		public static DiscordLogResult allowed() {
			return ALLOWED;
		}

		/**
		 * Prevents the log from being sent.
		 *
		 * @return the denied result
		 */
		public static DiscordLogResult denied() {
			return DENIED;
		}

		/**
		 * Allows the log to be sent, but only in public log channels
		 */
		public static DiscordLogResult publicOnly() {
			return new DiscordLogResult(true, LogVisibility.PUBLIC_ONLY);
		}

		/**
		 * Allows the log to be sent, but only in private log channels
		 */
		public static DiscordLogResult privateOnly() {
			return new DiscordLogResult(true, LogVisibility.PRIVATE_ONLY);
		}
	}
}

