package uk.co.notnull.proxydiscord;

import uk.co.notnull.platformdetection.PlatformDetectionVelocity;

public class PlatformDetectionHandler {
	private final PlatformDetectionVelocity platformDetection;

	public PlatformDetectionHandler(PlatformDetectionVelocity platformDetection) {
		this.platformDetection = platformDetection;
	}

	public PlatformDetectionVelocity getPlatformDetection() {
		return platformDetection;
	}
}
