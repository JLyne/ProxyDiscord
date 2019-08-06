package me.prouser123.bungee.discord.bot.commands;

import me.prouser123.bungee.discord.*;
import me.prouser123.bungee.discord.exceptions.AlreadyLinkedException;
import me.prouser123.bungee.discord.exceptions.InvalidTokenException;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

public class Link implements MessageCreateListener, BaseCommand {
	private final base base;

    public Link(int priority, String command, String helpText) {
	    base = easyBaseSetup(priority, command, helpText);
	}
	
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        LinkingManager linkingManager = Main.inst().getLinkingManager();

        //Fail fast if linking manager isn't ready yet
        if(linkingManager == null) {
            Main.inst().getLogger().warning("Ignoring link attempt before linking manager is ready.");
            String message = ChatMessages.getMessage("discord-link-error");
            event.getChannel().sendMessage(message.replace("[user]", "<@!" + event.getMessageAuthor().getId() + ">"));

            return;
        }

        //Ignore messages from other channels
        if(!linkingManager.isLinkingChannel(event.getChannel())) {
            return;
        }

        //Ignore random messages
        if(!event.getMessage().getContent().startsWith(base.command)) {
            return;
        }

        MessageAuthor author = event.getMessageAuthor();
        Long id = author.getId();
        String token = event.getMessageContent().replace("!link ", "");
        LinkResult result = LinkResult.UNKNOWN_ERROR;

        try {
            if(token.isEmpty()) {
                result = LinkResult.NO_TOKEN;
            } else {
                linkingManager.completeLink(token, id);

                if(Main.inst().getVerificationManager().hasVerifiedRole(id)) {
                    result = LinkResult.SUCCESS;
                } else {
                    result = LinkResult.NOT_VERIFIED;
                }
            }
        } catch (AlreadyLinkedException e) {
            result = LinkResult.ALREADY_LINKED;
        } catch (InvalidTokenException e) {
            result = LinkResult.INVALID_TOKEN;
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            if(true) {
                sendEmbedResponse(result, event);
            } else {
                sendResponse(result, event);
            }
        }
    }

    private void sendEmbedResponse(LinkResult result, MessageCreateEvent event) {
        EmbedBuilder embed = null;

        switch(result) {
            case UNKNOWN_ERROR:
                embed = ChatMessages.getEmbed("embed-link-error");
                break;

            case NO_TOKEN:
                embed = ChatMessages.getEmbed("embed-link-no-token");
                break;

            case INVALID_TOKEN:
                embed = ChatMessages.getEmbed("embed-link-invalid-token");
                break;

            case ALREADY_LINKED:
                embed = ChatMessages.getEmbed("embed-link-already-linked");
                break;

            case NOT_VERIFIED:
                embed = ChatMessages.getEmbed("embed-link-success-not-verified");
                break;

            case SUCCESS:
                embed = ChatMessages.getEmbed("embed-link-success");
                break;
        }

        if(embed != null) {
            Main.inst().getDebugLogger().info(embed.toString());
            event.getChannel().sendMessage("<@!" + event.getMessageAuthor().getId() + ">", embed);
        }
    }

    private void sendResponse(LinkResult result, MessageCreateEvent event) {
        String message = null;

        switch(result) {
            case UNKNOWN_ERROR:
                message = ChatMessages.getMessage("discord-link-error");
                break;

            case NO_TOKEN:
                message = ChatMessages.getMessage("discord-link-no-token");
                break;

            case INVALID_TOKEN:
                message = ChatMessages.getMessage("discord-link-invalid-token");
                break;

            case ALREADY_LINKED:
                message = ChatMessages.getMessage("discord-link-already-linked");
                break;

            case NOT_VERIFIED:
                message = ChatMessages.getMessage("discord-link-success-not-verified");
                break;

            case SUCCESS:
                message = ChatMessages.getMessage("discord-link-success");
                break;
        }

        if(message != null) {
            Main.inst().getDebugLogger().info(message);
            event.getChannel().sendMessage(message.replace("[user]", "<@!" + event.getMessageAuthor().getId() + ">"));
        }
    }

    private void sendEmbed(TextChannel channel, String userId) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("This is an embed")
                .setDescription("This is the embed description")
                .setThumbnail("https://cdn.discordapp.com/attachments/457984159458000908/608374556004057130/misc.png")
                .addField("Field 1", "Field 1 content")
                .setFooter("Bungee Discord " + Main.inst().getDescription().getVersion() + " | !bd"/*.split("-")[0]*/, "https://cdn.discordapp.com/avatars/215119410103451648/575d90fdda8663b633e36f8b8c06c719.png");

        channel.sendMessage("<@!" + userId + ">", embed);
    }
}