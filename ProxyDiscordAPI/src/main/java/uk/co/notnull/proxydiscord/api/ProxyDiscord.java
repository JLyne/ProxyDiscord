package uk.co.notnull.proxydiscord.api;

import uk.co.notnull.proxydiscord.api.manager.GroupSyncManager;
import uk.co.notnull.proxydiscord.api.manager.LinkingManager;
import uk.co.notnull.proxydiscord.api.manager.LoggingManager;
import uk.co.notnull.proxydiscord.api.manager.VerificationManager;

@SuppressWarnings("unused")
public interface ProxyDiscord {
	LinkingManager getLinkingManager();
	VerificationManager getVerificationManager();
	LoggingManager getLoggingManager();
	GroupSyncManager getGroupSyncManager();
}
