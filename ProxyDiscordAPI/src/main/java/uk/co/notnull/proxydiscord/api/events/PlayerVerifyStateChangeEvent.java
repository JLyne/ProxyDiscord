package uk.co.notnull.proxydiscord.api.events;

import com.velocitypowered.api.proxy.Player;
import uk.co.notnull.proxydiscord.api.VerificationResult;

public class PlayerVerifyStateChangeEvent {
	private final Player player;
	private final VerificationResult state;
	private final VerificationResult previousState;

	public PlayerVerifyStateChangeEvent(Player player, VerificationResult state, VerificationResult previousState) {
		this.player = player;
		this.state = state;
		this.previousState = previousState;
	}

	public PlayerVerifyStateChangeEvent(Player player, VerificationResult state) {
		this.player = player;
		this.state = state;
		this.previousState = VerificationResult.UNKNOWN;
	}

	public Player getPlayer() {
		return player;
	}

	public VerificationResult getState() {
		return state;
	}

	public VerificationResult getPreviousState() {
		return previousState;
	}
}
