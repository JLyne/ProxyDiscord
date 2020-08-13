package me.prouser123.bungee.discord.bot.commands;

import java.util.ArrayList;

import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import me.prouser123.bungee.discord.ProxyDiscord;

public class MainCommand implements MessageCreateListener, BaseCommand {
	static ArrayList<String> array;
	public static ArrayList<String> subArray;
	
	public MainCommand() {
		array = new ArrayList<>();
		subArray = new ArrayList<>();
	}
	
	/**
	 * Listener Command to show server information
	 * Usage: (DiscordApi - e.g. Discord.api) api.addMessageCreateListener(new ServerInfo());
	 */
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        // Check if the message content equals "!bd"
        if(event.getMessage().getContent().equalsIgnoreCase("!pd")) {
        	// Create and send the Main Command Embed
        	event.getChannel().sendMessage(this.createMainCommandEmbed(event));
        	event.getChannel().sendMessage(this.createSubCommandEmbed(event));
        }
    }
    
    @SuppressWarnings("unused")
	private EmbedBuilder createMainCommandEmbed(MessageCreateEvent event) {
    	EmbedBuilder embed = new EmbedBuilder().setTitle("Commands");
    	
    	for (String command: array) {
    		String[] split = command.split(this.arraySeperator);
    		
    		// Debug information
    		ProxyDiscord.inst().getDebugLogger().info("[MainCommand@OnMessage] Command: " + split[0] + ", HelpText: " + split[1]);

        	embed.addField(split[0], split[1]);
    	}
    	
    	// return the embed
    	return embed;
    }
    
    @SuppressWarnings("unused")
	private EmbedBuilder createSubCommandEmbed(MessageCreateEvent event) {
    	EmbedBuilder embed = new EmbedBuilder().setTitle("Sub-Commands");
    	
    	for (String command: subArray) {
    		String[] split = command.split(this.arraySeperator);
    		
    		// Debug information
    		ProxyDiscord.inst().getDebugLogger().info("[MainCommand@OnMessage] Command: " + split[0] + ", HelpText: " + split[1]);

    		embed.addField(split[0], split[1]);
    	}
    	
    	// return the embed
    	return embed;
    }
}