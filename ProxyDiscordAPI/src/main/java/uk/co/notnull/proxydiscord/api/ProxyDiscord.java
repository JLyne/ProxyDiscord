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

import uk.co.notnull.proxydiscord.api.emote.EmoteProvider;
import uk.co.notnull.proxydiscord.api.manager.GroupSyncManager;
import uk.co.notnull.proxydiscord.api.manager.LinkingManager;
import uk.co.notnull.proxydiscord.api.manager.LoggingManager;
import uk.co.notnull.proxydiscord.api.manager.VerificationManager;

/**
 * The ProxyDiscord plugin instance
 */
@SuppressWarnings("unused")
public interface ProxyDiscord {
	/**
	 * Gets the {@link LinkingManager} instance
	 * @return the linking manager
	 */
	LinkingManager getLinkingManager();

	/**
	 * Gets the {@link VerificationManager} instance
	 * @return the verification manager
	 */
	VerificationManager getVerificationManager();

	/**
	 * Gets the {@link LoggingManager} instance
	 * @return the logging manager
	 */
	LoggingManager getLoggingManager();

	/**
	 * Gets the {@link GroupSyncManager} instance
	 * @return the group sync manager
	 */
	GroupSyncManager getGroupSyncManager();

	EmoteProvider getEmoteProvider();

	void clearEmoteProvider();

	void setEmoteProvider(EmoteProvider provider);
}
