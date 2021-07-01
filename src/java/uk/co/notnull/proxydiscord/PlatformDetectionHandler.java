package uk.co.notnull.proxydiscord;

import com.velocitypowered.api.proxy.Player;
import uk.co.notnull.platformdetection.PlatformDetectionVelocity;

public class PlatformDetectionHandler {
	private final PlatformDetectionVelocity platformDetectionVelocity;

	public PlatformDetectionHandler(ProxyDiscord plugin) {
		platformDetectionVelocity = (PlatformDetectionVelocity) plugin.getProxy().getPluginManager()
				.getPlugin("platform-detection").get().getInstance().get();
	}

	public boolean isBedrock(Player player) {
		return platformDetectionVelocity.getPlatform(player).isBedrock();
	}
}
