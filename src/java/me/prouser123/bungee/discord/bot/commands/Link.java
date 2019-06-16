package me.prouser123.bungee.discord.bot.commands;

import me.prouser123.bungee.discord.ChatMessages;
import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.exceptions.AlreadyLinkedException;
import me.prouser123.bungee.discord.exceptions.InvalidTokenException;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

public class Link implements MessageCreateListener, BaseCommand {
    private static LinkingManager linkingManager;
	private base base;

	public Link(int piority, String command, String helpText) {
        linkingManager = Main.inst().getLinkingManager();

	    base = easyBaseSetup(piority, command, helpText);
	}
	
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        if(event.getMessage().getContent().startsWith(base.command)) {
            MessageAuthor author = event.getMessageAuthor();
            Long id = author.getId();
            String token = event.getMessageContent().replace("!link ", "");
            String message = null;

            try {
                linkingManager.completeLink(token , id);
                Main.inst().getVerificationManager().checkVerificationStatus(id);

                message = ChatMessages.getMessage("discord-link-success");
            } catch (AlreadyLinkedException e) {
                message = ChatMessages.getMessage("discord-link-already-linked");
                message = message.replace("[account]", linkingManager.getLinked(author.getId()));
            } catch (InvalidTokenException e) {
                message = ChatMessages.getMessage("discord-link-invalid-token");
            } finally {
                if(message != null) {
                    event.getChannel().sendMessage(message.replace("[user]", "<@!" + event.getMessageAuthor().getId() + ">"));
                }
            }
        }
    }

}