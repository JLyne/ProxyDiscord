package uk.co.notnull.proxydiscord;

import org.slf4j.Logger;

public class DebugLogger {
	private final Logger logger;

	private boolean debugEnabled = false;
	
	public void info(String message) {
		if(debugEnabled) {
			logger.info("[ProxyDiscord.DEBUG] " + message);
		}
	}
	
	DebugLogger(ProxyDiscord plugin) {
		this.logger = plugin.getLogger();

		if(plugin.getConfig().getNode("debug-enabled").getBoolean(false)) {
			debugEnabled = true;
			plugin.getLogger().info("Enabled debug logging.");
		}
	}
}