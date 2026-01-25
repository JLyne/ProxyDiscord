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

package uk.co.notnull.proxydiscord.bot.commands;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.LinkingManager;
import uk.co.notnull.proxydiscord.manager.LuckPermsManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Unlink extends ListenerAdapter {
    private final LinkingManager linkingManager;
    private final LuckPermsManager luckPermsManager;

    public Unlink(ProxyDiscord plugin) {
	    this.linkingManager = plugin.getLinkingManager();
	    this.luckPermsManager = plugin.getLuckpermsManager();
	}

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SlashCommandInteraction interaction = event.getInteraction();

        if(!interaction.getName().equals("unlink")) {
            return;
        }

        long userId = interaction.getUser().getIdLong();
        UUID linked = linkingManager.unlink(userId);
        CompletableFuture<MessageEmbed> response = getResponse(interaction.getUser(), linked);

        CompletableFuture.allOf(interaction.deferReply().submit(), response)
                .thenCompose(_ -> event.getHook().sendMessage(MessageCreateData.fromEmbeds(response.join())).submit())
                .exceptionally((e) -> {
                    e.printStackTrace();
                    event.getHook().sendMessage(MessageCreateData.fromEmbeds(
                            Messages.getEmbed("embed-unlink-error"))).queue();
                    return null;
                });
    }

    private CompletableFuture<MessageEmbed> getResponse(User user, UUID linked) {
        Map<String, String> replacements = new HashMap<>(Map.of("discord", user.getAsMention()));

        //User was linked
        if(linked != null) {
            return CompletableFuture.supplyAsync(() -> {
                String username = luckPermsManager.getUserManager().lookupUsername(linked).join();
                replacements.put("minecraft", (username != null) ? username : "Unknown account (" + linked + ")");

                return Messages.getEmbed("embed-unlink-success", replacements);
            });
        } else { //User wasn't linked
            return CompletableFuture.completedFuture(Messages.getEmbed("embed-unlink-not-linked", replacements));
        }
    }
}