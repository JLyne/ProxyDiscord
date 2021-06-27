package uk.co.notnull.proxydiscord.api.manager;

import uk.co.notnull.proxydiscord.api.logging.LogEntry;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
public interface LoggingManager {
	CompletableFuture<Void> logEvent(LogEntry entry);
	void logCustomEvent(LogEntry entry);
}
