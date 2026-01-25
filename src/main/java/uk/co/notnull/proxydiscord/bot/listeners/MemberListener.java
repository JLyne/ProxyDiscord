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

package uk.co.notnull.proxydiscord.bot.listeners;

import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.GroupSyncManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;

public class MemberListener extends ListenerAdapter {
	private final VerificationManager verificationManager;
	private final GroupSyncManager groupSyncManager;

	public MemberListener(ProxyDiscord plugin) {
		this.verificationManager = plugin.getVerificationManager();
		this.groupSyncManager = plugin.getGroupSyncManager();
	}

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
		verificationManager.handleRoleAdd(event.getUser(), event.getMember().getRoles());
		groupSyncManager.handleMemberJoin(event.getMember());
    }

	@Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
		verificationManager.handleRoleRemove(event.getUser(), event.getGuild().getRoles());
		groupSyncManager.handleMemberRemove(event.getUser(), event.getGuild());
    }
}


