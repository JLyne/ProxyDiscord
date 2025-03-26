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
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.LinkingManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Unlink implements MessageCreateListener, SlashCommandCreateListener {
    private final LinkingManager linkingManager;
    private final UserManager userManager;

    private ListenerManager<MessageCreateListener> messageListener;
    private ListenerManager<SlashCommandCreateListener> slashCommandListener;
    private long linkingChannelId;

    public Unlink(LinkingManager linkingManager, ServerTextChannel linkingChannel) {
	    this.linkingManager = linkingManager;
	    this.userManager = ProxyDiscord.inst().getLuckpermsManager().getUserManager();

	    setLinkingChannel(linkingChannel);
	}

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        String content = event.getMessageContent();

        //Ignore random messages
        if(!event.getMessageAuthor().isRegularUser() || (!content.startsWith("!unlink") && !content.startsWith("/unlink"))) {
            return;
        }

        if(event.getChannel().getId() != linkingChannelId) {
            return;
        }

        long userId = event.getMessageAuthor().getId();
        UUID linked = linkingManager.unlink(userId);

        getResponse(userId, linked)
                .thenAccept((EmbedBuilder e) -> event.getMessage().reply(e)).exceptionally((e) -> {
                    e.printStackTrace();
                    event.getMessage().reply(Messages.getEmbed("embed-unlink-error"));
                    return null;
                });
    }

    @Override
    public void onSlashCommandCreate(SlashCommandCreateEvent event) {
        SlashCommandInteraction interaction = event.getSlashCommandInteraction();

        if(!interaction.getCommandName().equals("unlink")) {
            return;
        }

        long userId = interaction.getUser().getId();
        UUID linked = linkingManager.unlink(userId);

        getResponse(userId, linked)
                .thenAccept((EmbedBuilder e) -> interaction.createImmediateResponder()
                        .addEmbed(e)
                        .respond())
                .exceptionally((e) -> {
                    e.printStackTrace();
                    interaction.createImmediateResponder()
                            .addEmbed(Messages.getEmbed("embed-unlink-error"))
                            .respond();
                    return null;
                });
    }

    private CompletableFuture<EmbedBuilder> getResponse(long userId, UUID linked) {
        Map<String, String> replacements = new HashMap<>(Map.of("discord", "<@!" + userId + ">"));

        //User was linked
        if(linked != null) {
            return CompletableFuture.supplyAsync(() -> {
                String username = userManager.lookupUsername(linked).join();
                replacements.put("minecraft", (username != null) ? username : "Unknown account (" + linked + ")");

                return Messages.getEmbed("embed-unlink-success", replacements);
            });
        } else { //User wasn't linked
            return CompletableFuture.completedFuture(Messages.getEmbed("embed-unlink-not-linked", replacements));
        }
    }

    public void setLinkingChannel(ServerTextChannel linkingChannel) {
        remove();

        if(linkingChannel != null) {
            linkingChannelId = linkingChannel.getId();
            messageListener = linkingChannel.addMessageCreateListener(this);
            slashCommandListener = linkingChannel.getApi().addSlashCommandCreateListener(this);
        }
    }

    public void remove() {
        if(messageListener != null) {
            messageListener.remove();
        }

        if(slashCommandListener != null) {
            slashCommandListener.remove();
        }
    }
}