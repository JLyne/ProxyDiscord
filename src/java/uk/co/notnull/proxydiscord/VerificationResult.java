package uk.co.notnull.proxydiscord;

public enum VerificationResult {
    UNKNOWN(false), // Status hasn't been calculated yet
    NOT_LINKED(false), // Player hasn't linked a discord account
    LINKED_NOT_VERIFIED(false), // Player has linked a discord account but doesn't have any verified roles
    VERIFIED(true), // Player has linked a discord account and has a verified role
    BYPASSED(true), // Player has the bypass permission
    NOT_REQUIRED(true); // No verified roles are configured

    private boolean verified = false;

    VerificationResult(boolean verified) {
        this.verified = verified;
    }

    public boolean isVerified() {
        return verified;
    }
}
