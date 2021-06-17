package uk.co.notnull.proxydiscord.bot.commands;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;
import uk.co.notnull.proxydiscord.ChatMessages;
import uk.co.notnull.proxydiscord.LinkResult;
import uk.co.notnull.proxydiscord.LinkingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Link implements MessageCreateListener {
    private final LinkingManager linkingManager;
    private ListenerManager<MessageCreateListener> messageListener;

    public Link(LinkingManager linkingManager, TextChannel linkingChannel) {
	    this.linkingManager = linkingManager;
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
        } finally {
            sendResponse(result, event);
        }
    }

    private void sendResponse(LinkResult result, MessageCreateEvent event) {
        LinkingManager linkingManager = ProxyDiscord.inst().getLinkingManager();
        LuckPerms luckPermsApi = LuckPermsProvider.get();

        CompletableFuture<EmbedBuilder> embed = null;
        UUID linked = linkingManager.getLinked(event.getMessageAuthor().getId());

        switch(result) {
            case UNKNOWN_ERROR:
                embed = CompletableFuture.completedFuture(ChatMessages.getEmbed("embed-link-error"));
                break;

            case NO_TOKEN:
                embed = CompletableFuture.completedFuture(ChatMessages.getEmbed("embed-link-no-token"));
                break;

            case INVALID_TOKEN:
                embed = CompletableFuture.completedFuture(ChatMessages.getEmbed("embed-link-invalid-token"));
                break;

            //FIXME: Reduce duplication here
            case ALREADY_LINKED:
                embed = CompletableFuture.supplyAsync(() -> {
                    String username = luckPermsApi.getUserManager().lookupUsername(linked).join();
                    Map<String, String> replacements = Map.of(
                            "[discord]", "<@!" + event.getMessageAuthor().getId() + ">",
                            "[minecraft]", (username != null) ? username : "Unknown account (" + linked + ")");

                    return ChatMessages.getEmbed("embed-link-already-linked", replacements);
                });
                break;

            case NOT_VERIFIED:
            case ALREADY_LINKED_NOT_VERIFIED:
                embed = CompletableFuture.supplyAsync(() -> {
                    String username = luckPermsApi.getUserManager().lookupUsername(linked).join();
                    Map<String, String> replacements = Map.of(
                            "[discord]", "<@!" + event.getMessageAuthor().getId() + ">",
                            "[minecraft]", (username != null) ? username : "Unknown account (" + linked + ")");

                    return ChatMessages.getEmbed("embed-link-success-not-verified", replacements);
                });
                break;

            case SUCCESS:
                embed = CompletableFuture.supplyAsync(() -> {
                    String username = luckPermsApi.getUserManager().lookupUsername(linked).join();
                    Map<String, String> replacements = Map.of(
                            "[discord]", "<@!" + event.getMessageAuthor().getId() + ">",
                            "[minecraft]", (username != null) ? username : "Unknown account (" + linked + ")");

                    return ChatMessages.getEmbed("embed-link-success", replacements);
                });
                break;
        }

        embed.thenAccept((EmbedBuilder e) -> event.getMessage().reply(e));
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