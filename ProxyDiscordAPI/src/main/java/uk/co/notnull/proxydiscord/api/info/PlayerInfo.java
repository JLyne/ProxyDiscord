/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2022 James Lyne
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

package uk.co.notnull.proxydiscord.api.info;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
public class PlayerInfo {
	private final UUID uuid;
	private String username;
	private boolean online;
	private RegisteredServer currentServer = null;
	private boolean vanished = false;
	private QueueInfo queueInfo = null;
	private final List<ExtraField> extraFields = new ArrayList<>();

	public PlayerInfo(UUID uuid, String username) {
		this.uuid = uuid;
		this.username = username;
	}

	public record QueueInfo(@NotNull RegisteredServer server, String position) {
		public QueueInfo {
			java.util.Objects.requireNonNull(server);
			java.util.Objects.requireNonNull(position);
		}
	}

	public record ExtraField(@NotNull String name, @NotNull String value, boolean inline) {
		public ExtraField {
			java.util.Objects.requireNonNull(name);
			java.util.Objects.requireNonNull(value);
		}
	}

	public String getUsername() {
		return username;
	}

	public UUID getUuid() {
		return uuid;
	}

	public boolean isOnline() {
		return online;
	}

	public RegisteredServer getCurrentServer() {
		return currentServer;
	}

	public boolean isVanished() {
		return vanished;
	}

	public QueueInfo getQueueInfo() {
		return queueInfo;
	}

	public List<ExtraField> getExtraFields() {
		return extraFields;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setOnline(boolean online) {
		this.online = online;
	}

	public void setCurrentServer(RegisteredServer currentServer) {
		this.currentServer = currentServer;
	}

	public void setVanished(boolean vanished) {
		this.vanished = vanished;
	}

	public void setQueueInfo(QueueInfo queueInfo) {
		this.queueInfo = queueInfo;
	}

	public void addExtraField(ExtraField field) {
		extraFields.add(field);
	}

	public void addExtraFields(List<ExtraField> fields) {
		extraFields.addAll(fields);
	}
}