package uk.co.notnull.proxydiscord;

import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;

public class DebugLogger {
	private final Logger logger;

	private boolean debugEnabled = false;
	
	public void info(String message) {
		if(debugEnabled) {
			logger.info("[ProxyDiscord.DEBUG] " + message);
		}
	}
	
	DebugLogger(ProxyDiscord plugin, ConfigurationNode config) {
		this.logger = plugin.getLogger();

		parseConfig(config);
	}

	private void parseConfig(ConfigurationNode config) {
		debugEnabled = config.getNode("debug-enabled").getBoolean(false);

		if(debugEnabled) {
			logger.info("Enabled debug logging.");
		}
	}

	public void reload(ConfigurationNode config) {
		parseConfig(config);
	}
}