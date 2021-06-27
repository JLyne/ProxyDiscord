package uk.co.notnull.proxydiscord.api.logging;

public enum LogVisibility {
	UNSPECIFIED,
	PUBLIC_ONLY,
	PRIVATE_ONLY;

	public boolean isPublic() {
		return this != PRIVATE_ONLY;
	}

	public boolean isPrivate() {
		return this != PUBLIC_ONLY;
	}
}
