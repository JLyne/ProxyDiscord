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
import uk.co.notnull.proxydiscord.api.LinkResult;

import java.util.UUID;

/**
 * Manager class for tracking and updating the Discord links of Minecraft players
 */
@SuppressWarnings("unused")
public interface LinkingManager {
    /**
     * Gets whether a player has linked a Discord account
     * @param player The player to check
     * @return whether the player has linked a Discord account
     */
	boolean isLinked(Player player);

	/**
     * Gets whether a player with the given UUID has linked a Discord account
     * @param uuid The UUID to check
     * @return whether the player has linked a Discord account
     */
    boolean isLinked(UUID uuid);

    /**
     * Gets whether a Discord account with the given ID has been linked to a Minecraft account
     * @param discordId The ID to check
     * @return whether the Discord account has been linked to a Minecraft account
     */
    boolean isLinked(long discordId);

    /**
     * Gets the UUID of the Minecraft account linked to the given Discord account ID
     * @param discordId The ID
     * @return the UUID of the linked Minecraft account, or null if no account is linked
     */
    UUID getLinked(Long discordId);

    /**
     * Gets the ID of the Discord account linked to the given player
     * @param player The player
     * @return the ID of the linked Discord account, or null if no account is linked
     */
    Long getLinked(Player player);

    /**
     * Gets the ID of the Discord account linked to the given UUID
     * @param uuid The UUID
     * @return the ID of the linked Discord account, or null if no account is linked
     */
    Long getLinked(UUID uuid);

    /**
     * Gets the linking token for the given player, generating one if required
     * @param player The player
     * @return the linking token
     */
    String getLinkingToken(Player player);

    /**
     * Gets the linking token for the given UUID, generating one if required
     * @param uuid The UUID
     * @return the linking token
     */
    String getLinkingToken(UUID uuid);

    /**
     * Links the given Discord account ID to the Minecraft account associated with the given linking token
     * @param token The linking token
     * @param discordId The Discord account ID to link
     * @return {@link LinkResult} indicating the result of the operation
     */
    LinkResult completeLink(String token, Long discordId);

    /**
     * Manually links the given Discord account ID and Minecraft account UUID, without the use of a linking token
     * @param uuid The Minecraft account UUID to link
     * @param discordId The Discord account ID to link
     * @return {@link LinkResult} indicating the result of the operation
     */
    LinkResult manualLink(UUID uuid, Long discordId);

    /**
     * Unlinks any linked Discord account for the specified player
     * @param player The player
     */
    void unlink(Player player);

    /**
     * Unlinks any linked Discord account for the specified UUID
     * @param uuid The UUID
     */
    void unlink(UUID uuid);

    /**
     * Unlinks any linked Minecraft account for the specified Discord ID
     * @param discordId The Discord ID
     */
    void unlink(long discordId);

    /**
     * Gets the currently configured linking secret
     * @return The secret
     */
    String getLinkingSecret();
}
