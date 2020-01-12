package me.prouser123.bungee.discord;

public class DebugLogger {
	
	private boolean debugEnabled = false;
	
	public void info(String message) {
		if(debugEnabled) {
			ProxyDiscord.inst().getLogger().info("[ProxyDiscord.DEBUG] " + message);
		}
	}
	
	DebugLogger() {
		try {
			if(ProxyDiscord.inst().getConfig().getNode("debug-enabled").getBoolean(false)) {
				debugEnabled = true;
				ProxyDiscord.inst().getLogger().info("Enabled debug logging.");
			}
		} catch (NullPointerException e) {
			// Coudn't find the boolean, just return for now.
		}
	}
}