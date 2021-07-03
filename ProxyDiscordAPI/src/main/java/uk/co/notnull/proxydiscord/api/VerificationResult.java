/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2021 James Lyne
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package uk.co.notnull.proxydiscord.api;

/**
 * A player's verification status
 */
public enum VerificationResult {
    /**
     * Status hasn't been calculated yet
     */
    UNKNOWN(false),

    /**
     * The player hasn't linked a discord account
     */
    NOT_LINKED(false),

    /**
     * The player has linked a discord account but doesn't have any of the configured verified roles
     */
    LINKED_NOT_VERIFIED(false),

    /**
     * The player has linked a discord account and has a one or more of the configured verified roles
     */
    VERIFIED(true),

    /**
     * The player has the configured bypass permission and skipped all other requirement checks
     */
    BYPASSED(true),

    /**
     * No verified roles have been configured and all checks were skipped
     */
    NOT_REQUIRED(true);

    private final boolean verified;

    VerificationResult(boolean verified) {
        this.verified = verified;
    }

    /**
     * Gets whether a result should be considered "verified" for actions requiring a verified user
     * (i.e joining non-public servers etc)
     * @return whether the result is considered "verified"
     */
    public boolean isVerified() {
        return verified;
    }
}
