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

package uk.co.notnull.proxydiscord.api.logging;

/**
 * Determines the logging channels that a {@link LogEntry} will be sent to
 */
public enum LogVisibility {
	/**
	 * Will be shown in all logging channels
	 */
	UNSPECIFIED,

	/**
	 * Will only be shown in `public: true` logging channels
	 */
	PUBLIC_ONLY,

	/**
	 * Will only be shown in `public: false` logging channels
	 */
	PRIVATE_ONLY;

	/**
	 * Gets whether a {@link LogVisibility} should be shown in `public: true` channels
	 * @return boolean indicating whether message should be shown
	 */
	public boolean isPublic() {
		return this != PRIVATE_ONLY;
	}

	/**
	 * Gets whether a {@link LogVisibility} should be shown in `public: false` channels
	 * @return boolean indicating whether message should be shown
	 */
	public boolean isPrivate() {
		return this != PUBLIC_ONLY;
	}
}
