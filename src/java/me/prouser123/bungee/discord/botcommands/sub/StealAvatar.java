package me.prouser123.bungee.discord.botcommands.sub;

import me.prouser123.bungee.discord.Main;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import me.prouser123.bungee.discord.base.BaseSubCommand;

import java.util.concurrent.ExecutorService;

public class StealAvatar implements MessageCreateListener, BaseSubCommand {
	
	private base base;
	
	public StealAvatar(int piority, String command, String helpText) {
		base = this.easyBaseSetup(piority, command, helpText);
	}
	
	/**
	 * Listener Command. When run by the Bot Owner, it will set the Bot's avatar icon to that of that user.
	 * Usage: api.addMessageCreateListener(new CopyOwnerAvatar());
	 */
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        // Check if the message content equals "!copyAvatar"
        if (event.getMessage().getContent().equalsIgnoreCase(base.command)) {

            // Check if the author is the creator of the bot (you!).
            // You don't want that everyone can set the bot's avatar.
            if (!event.getMessage().getAuthor().isBotOwner()) {
                event.getChannel().sendMessage("You are not allowed to use this command!");
                return;
            }

            ExecutorService executor = Main.inst().getExecutorService();

            event.getApi()
                    .updateAvatar(event.getMessage().getAuthor().getAvatar()) // Update the avatar
                    .thenAcceptAsync(
                            aVoid -> event.getChannel().sendMessage("Now using the avatar of: " + event.getMessage().getAuthor().getName()),
                            executor
                    ) // Send the user a message if the update was successful
                    .exceptionally(throwable -> {
                        // Send the user a message, if the update failed
                        event.getChannel().sendMessage("Something went wrong: " + throwable.getMessage());
                        return null;
                    });
            return;
        }
    }

}