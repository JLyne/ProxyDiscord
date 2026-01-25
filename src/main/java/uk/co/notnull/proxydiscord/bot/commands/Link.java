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
import uk.co.notnull.proxydiscord.api.LinkResult;
import uk.co.notnull.proxydiscord.manager.LinkingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.LuckPermsManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import uk.co.notnull.proxydiscord.api.VerificationResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Link extends ListenerAdapter {
    private final LinkingManager linkingManager;
    private final LuckPermsManager luckPermsManager;

    public Link(ProxyDiscord plugin) {
	    this.linkingManager = plugin.getLinkingManager();
        this.luckPermsManager = plugin.getLuckpermsManager();
	}

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SlashCommandInteraction interaction = event.getInteraction();
        LinkResult result;

        if(!interaction.getName().equals("link")) {
            return;
        }

        try {
            String token = interaction.getOption("token").getAsString().toUpperCase();
            result = linkingManager.completeLink(token, interaction.getUser().getIdLong());
        } catch (Exception e) {
            e.printStackTrace();
            result = LinkResult.UNKNOWN_ERROR;
        }

        getResponse(result, interaction.getUser())
                .thenCompose(embed -> interaction.reply(MessageCreateData.fromEmbeds(embed)).submit())
                .exceptionally((e) -> {
                    e.printStackTrace();
                    interaction.reply(MessageCreateData.fromEmbeds(Messages.getEmbed("embed-link-error"))).queue();
                    return null;
                });
    }

    private CompletableFuture<MessageEmbed> getResponse(LinkResult result, User user) {
        VerificationManager verificationManager = ProxyDiscord.inst().getVerificationManager();
        CompletableFuture<MessageEmbed> embed = null;
        UUID linked = linkingManager.getLinked(user);

        Map<String, String> replacements = new HashMap<>(Map.of("discord", user.getAsMention()));

        switch(result) {
            case UNKNOWN_ERROR:
                embed = CompletableFuture.completedFuture(Messages.getEmbed("embed-link-error"));
                break;

            case NO_TOKEN:
                embed = CompletableFuture.completedFuture(Messages.getEmbed("embed-link-no-token"));
                break;

            case INVALID_TOKEN:
                embed = CompletableFuture.completedFuture(Messages.getEmbed("embed-link-invalid-token"));
                break;

            case ALREADY_LINKED:
                embed = CompletableFuture.supplyAsync(() -> {
                    String username = luckPermsManager.getUserManager().lookupUsername(linked).join();
                    replacements.put("minecraft", (username != null) ? username : "Unknown account (" + linked + ")");

                    VerificationResult verificationResult = verificationManager.checkVerificationStatus(user);

                    if(verificationResult.isVerified()) {
                        return Messages.getEmbed("embed-link-already-linked", replacements);
                    } else {
                        return Messages.getEmbed("embed-link-success-not-verified", replacements);
                    }
                });
                break;

            case SUCCESS:
                embed = CompletableFuture.supplyAsync(() -> {
                    String username = luckPermsManager.getUserManager().lookupUsername(linked).join();
                    replacements.put("minecraft", (username != null) ? username : "Unknown account (" + linked + ")");

                    VerificationResult verificationResult = verificationManager.checkVerificationStatus(user);

                    if(verificationResult.isVerified()) {
                        return Messages.getEmbed("embed-link-success", replacements);
                    } else {
                        return Messages.getEmbed("embed-link-success-not-verified", replacements);
                    }
                });
                break;
        }

        return embed;
    }
}