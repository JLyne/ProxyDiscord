package me.prouser123.bungee.discord.bot.commands;

import me.prouser123.bungee.discord.ChatMessages;
import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.VerificationManager;
import me.prouser123.bungee.discord.exceptions.AlreadyLinkedException;
import me.prouser123.bungee.discord.exceptions.InvalidTokenException;
import org.javacord.api.entity.message.MessageAuthor;
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
        String message = null;

        try {
            if(token.isEmpty()) {
                message = ChatMessages.getMessage("discord-link-no-token");
            } else {
                linkingManager.completeLink(token, id);

                if(Main.inst().getVerificationManager().hasVerifiedRole(id)) {
                    message = ChatMessages.getMessage("discord-link-success");
                } else {
                    message = ChatMessages.getMessage("discord-link-success-not-verified");
                }
            }
        } catch (AlreadyLinkedException e) {
            message = ChatMessages.getMessage("discord-link-already-linked");
            message = message.replace("[account]", linkingManager.getLinked(author.getId()));
        } catch (InvalidTokenException e) {
            message = ChatMessages.getMessage("discord-link-invalid-token");
        } catch(Exception e) {
            message = ChatMessages.getMessage("discord-link-error");
            e.printStackTrace();
        } finally {
            Main.inst().getDebugLogger().info(message);
            if(message != null) {
                event.getChannel().sendMessage(message.replace("[user]", "<@!" + event.getMessageAuthor().getId() + ">"));
            }
        }
    }
}