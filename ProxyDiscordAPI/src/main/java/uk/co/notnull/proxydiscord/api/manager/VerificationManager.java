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

package uk.co.notnull.proxydiscord.api.manager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import uk.co.notnull.proxydiscord.api.VerificationResult;

import java.util.Set;

/**
 * Manager class for calculating and updating the verification state of Minecraft players
 */
public interface VerificationManager {
    /**
     * Gets the verification status of the given player
     * @param player The player to check
     * @return {@link VerificationResult} indicating the player's current status
     */
	VerificationResult checkVerificationStatus(Player player);

	/**
     * Gets the verification status of the given Discord account ID
     * @param discordId The Discout account ID to check
     * @return {@link VerificationResult} indicating the account's current status
     */
    VerificationResult checkVerificationStatus(Long discordId);

    /**
     * Gets a list of the currently configured public servers
     * @return The server list
     */
    Set<RegisteredServer> getPublicServers();

    /**
     * Gets whether the given server is configured as a public server, requiring no verification to join
     * @param server The server to check
     * @return whether the server is considered public
     */
    boolean isPublicServer(RegisteredServer server);

    /**
     * Gets the configured default verified server, which newly-verified players will be sent to
     * if they have no other known destination
     * @return The server
     */
    RegisteredServer getDefaultVerifiedServer();

    /**
     * Gets the configured linking server, which players will be sent to when they do not meet the
     * requirements for their original destination server
     * @return The server
     */
    RegisteredServer getLinkingServer();

    /**
     * Gets whther the given server is the currently configured linking server
     * @param server The server
     * @return whether server is the linking server
     */
    boolean isLinkingServer(RegisteredServer server);
}
