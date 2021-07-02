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

import net.luckperms.api.model.user.UserManager;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.api.LinkResult;
import uk.co.notnull.proxydiscord.manager.LinkingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import uk.co.notnull.proxydiscord.api.VerificationResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Link implements MessageCreateListener {
    private final LinkingManager linkingManager;
    private final UserManager userManager;
    private ListenerManager<MessageCreateListener> messageListener;

    public Link(LinkingManager linkingManager, TextChannel linkingChannel) {
	    this.linkingManager = linkingManager;
	    this.userManager = ProxyDiscord.inst().getLuckpermsManager().getUserManager();
	    setLinkingChannel(linkingChannel);
	}

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        String command = "!link";

        //Ignore random messages
        if(!event.getMessage().getContent().startsWith(command)) {
            return;
        }

        MessageAuthor author = event.getMessageAuthor();
        Long id = author.getId();
        String token = event.getMessageContent().replace(command + " ", "").toUpperCase();
        LinkResult result = LinkResult.UNKNOWN_ERROR;

        try {
            result = linkingManager.completeLink(token, id);
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(LinkResult.UNKNOWN_ERROR, event);
        } finally {
            sendResponse(result, event);
        }
    }

    private void sendResponse(LinkResult result, MessageCreateEvent event) {
        VerificationManager verificationManager = ProxyDiscord.inst().getVerificationManager();
        CompletableFuture<EmbedBuilder> embed = null;
        UUID linked = linkingManager.getLinked(event.getMessageAuthor().getId());

        Map<String, String> replacements = new HashMap<>(
                Map.of("[discord]", "<@!" + event.getMessageAuthor().getId() + ">"));

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
                    String username = userManager.lookupUsername(linked).join();
                    replacements.put("[minecraft]", (username != null) ? username : "Unknown account (" + linked + ")");

                    VerificationResult verificationResult = verificationManager.checkVerificationStatus(
                            event.getMessageAuthor().getId());

                    if(verificationResult.isVerified()) {
                        return Messages.getEmbed("embed-link-already-linked", replacements);
                    } else {
                        return Messages.getEmbed("embed-link-success-not-verified", replacements);
                    }
                });
                break;

            case SUCCESS:
                embed = CompletableFuture.supplyAsync(() -> {
                    String username = userManager.lookupUsername(linked).join();
                    replacements.put("[minecraft]", (username != null) ? username : "Unknown account (" + linked + ")");

                    VerificationResult verificationResult = verificationManager.checkVerificationStatus(
                            event.getMessageAuthor().getId());

                    if(verificationResult.isVerified()) {
                        return Messages.getEmbed("embed-link-success", replacements);
                    } else {
                        return Messages.getEmbed("embed-link-success-not-verified", replacements);
                    }
                });
                break;
        }

        embed.thenAccept((EmbedBuilder e) -> event.getMessage().reply(e)).exceptionally((e) -> {
            e.printStackTrace();
            event.getMessage().reply(Messages.getEmbed("embed-link-error"));
            return null;
        });
    }

    public void setLinkingChannel(TextChannel linkingChannel) {
        if(messageListener != null) {
            messageListener.remove();
        }

        messageListener = linkingChannel.addMessageCreateListener(this);
    }

    public void remove() {
        if(messageListener != null) {
            messageListener.remove();
        }
    }
}