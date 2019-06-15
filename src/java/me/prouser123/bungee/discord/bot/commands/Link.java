package me.prouser123.bungee.discord.bot.commands;

import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.VerificationManager;
import me.prouser123.bungee.discord.exceptions.AlreadyLinkedException;
import me.prouser123.bungee.discord.exceptions.InvalidTokenException;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

public class Link implements MessageCreateListener, BaseCommand {
    private static LinkingManager linkingManager;
    private static VerificationManager verificationManager;
	private base base;

	public Link(int piority, String command, String helpText) {
        linkingManager = Main.inst().getLinkingManager();
        verificationManager = Main.inst().getVerificationManager();

	    base = easyBaseSetup(piority, command, helpText);
	}
	
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        if(event.getMessage().getContent().startsWith(base.command)) {
            MessageAuthor author = event.getMessageAuthor();
            Long id = author.getId();
            String token = event.getMessageContent().replace("!link ", "");

            try {
                linkingManager.completeLink(token , id);
                verificationManager.checkVerificationStatus(id);

                event.getChannel().sendMessage("<@!" + author.getId() + "> Haha yes account linked xd");
            } catch (AlreadyLinkedException e) {
                event.getChannel().sendMessage("<@!" + author.getId() + "> Sorry, your account is already linked to PersonName etc etc");
            } catch (InvalidTokenException e) {
                event.getChannel().sendMessage("<@!" + author.getId() + "> Sorry, that token isn't valid. Please double check.");
            }
        }
    }

}