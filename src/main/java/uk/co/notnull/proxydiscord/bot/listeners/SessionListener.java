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

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildUnavailableEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jspecify.annotations.NonNull;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.AnnouncementManager;
import uk.co.notnull.proxydiscord.manager.GroupSyncManager;
import uk.co.notnull.proxydiscord.manager.LoggingManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;

public class SessionListener extends ListenerAdapter {
    private final VerificationManager verificationManager;
    private final GroupSyncManager groupSyncManager;
    private final AnnouncementManager announcementManager;
    private final LoggingManager loggingManager;

    public SessionListener(ProxyDiscord plugin) {
        this.verificationManager = plugin.getVerificationManager();
        this.groupSyncManager = plugin.getGroupSyncManager();
        this.announcementManager = plugin.getAnnouncementManager();
        this.loggingManager = plugin.getLoggingManager();
    }

    public void onGuildReady(@NonNull GuildReadyEvent event) {
        ProxyDiscord.inst().getLogger().info("{} is ready", event.getGuild().getName());
        handleNewGuild(event.getGuild());
    }

    public void onGuildAvailable(@NonNull GuildAvailableEvent event) {
        ProxyDiscord.inst().getLogger().info("{} is now available", event.getGuild().getName());
        handleNewGuild(event.getGuild());
    }

    public void onGuildUnavailable(@NonNull GuildUnavailableEvent event) {
        ProxyDiscord.inst().getLogger().info("{} is unavailable", event.getGuild().getName());
        handleRemovedGuild(event.getGuild());
    }

    public void onGuildLeave(@NonNull GuildLeaveEvent event) {
        ProxyDiscord.inst().getLogger().info("Bot has left {}", event.getGuild().getName());
        handleRemovedGuild(event.getGuild());
        verificationManager.removeUsers(event.getGuild());
        groupSyncManager.removeUsers(event.getGuild());
    }

    public void onGuildJoin(@NonNull GuildJoinEvent event) {
        ProxyDiscord.inst().getLogger().info("Bot has joined {}", event.getGuild().getName());
        handleNewGuild(event.getGuild());
    }

    private void handleNewGuild(Guild guild) {
        verificationManager.populateUsers(guild);
        groupSyncManager.populateUsers(guild);
        announcementManager.findChannels(guild);
        loggingManager.findChannels(guild);
    }

    private void handleRemovedGuild(Guild guild) {
        announcementManager.removeChannels(guild);
        loggingManager.removeChannels(guild);
    }
}


