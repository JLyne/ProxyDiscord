package me.prouser123.bungee.discord;

import me.prouser123.bungee.discord.bot.commands.Link;
import me.prouser123.bungee.discord.bot.commands.MainCommand;
import me.prouser123.bungee.discord.bot.commands.Players;
import me.prouser123.bungee.discord.bot.commands.ServerInfo;
import me.prouser123.bungee.discord.bot.commands.sub.BotInfo;
import me.prouser123.bungee.discord.bot.commands.sub.Debug;
import net.md_5.bungee.config.Configuration;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.javacord.api.DiscordApi;

public class Discord {
	
	public static String token = null; // Bot token
	
	/**
	 * Discord API Instance
	 */
	public static DiscordApi api = null; // Api Instance

	private Configuration commandConfiguration;

	/**
	 * Class
	 * @param token bot token
	 */
	public Discord(String token, Configuration commandConfiguration) {
		Discord.token = token;
		this.commandConfiguration = commandConfiguration;

		// Create an Instance of the DiscordApi
		try {
			api = new DiscordApiBuilder().setToken(token).login().join();
		} catch (CompletionException IllegalStateException) {
			Main.inst().getLogger().info("Connection Error. Did you put a valid token in the config?");
		}
		
        // Print the invite url of the bot
        Main.inst().getLogger().info("Bot Invite Link: " + api.createBotInvite());

		// Cache a maximum of 10 messages per channel for and remove messages older than 1 hour
		api.setMessageCacheSize(10, 60*60);

		// Set Activity
        api.updateActivity(Constants.activity);
        
        // Create server join Listeners
        api.addServerJoinListener(event -> Main.inst().getLogger().info("Joined Server: " + event.getServer().getName()));
        api.addServerLeaveListener(event -> Main.inst().getLogger().info("Left Server: " + event.getServer().getName()));
        
        // Add Reconnect Listener to re-add status
        api.addReconnectListener(event -> {
        	Main.inst().getLogger().info(("Reconnected to Discord."));
        	api.updateActivity(Constants.activity);
        });
        
        api.addResumeListener(event -> {
        	Main.inst().getLogger().info(("Resumed connection to Discord."));
        	api.updateActivity(Constants.activity);
        });

        registerCommands();
        registerSubCommands();
	}

	private void registerCommands() {
		Main.inst().getLogger().info("Registering bot...");

		// Register Main Command Class
		api.addMessageCreateListener(new MainCommand());

		// Attempt to register server-info from the config, falling back to the defaults.
		if (commandConfiguration.contains("server-info")) {
			api.addMessageCreateListener(new ServerInfo(0, commandConfiguration.getString("server-info.command"), commandConfiguration.getString("server-info.description")));
		} else {
			Main.inst().getLogger().warning("[Bot Command Options] Missing the server-info path. You will not be able to customize the !serverinfo command.");
			api.addMessageCreateListener(new ServerInfo(0, "!serverinfo", "Show server information."));
		}

		// Attempt to register players from the config, falling back to the defaults.
		if (commandConfiguration.contains("players")) {
			api.addMessageCreateListener(new Players(1, commandConfiguration.getString("players.command"), commandConfiguration.getString("players.description")));
		} else {
			Main.inst().getLogger().warning("[Bot Command Options] Missing the players path. You will not be able to customize the !players command.");
			api.addMessageCreateListener(new Players(1, "!players", "Show players currently on the network and their servers."));
		}

		api.addMessageCreateListener(new Link(2, "!link", "Allows players to link their discord account"));
	}

	private void registerSubCommands() {
		Main.inst().getLogger().info("Registering sub-bot...");

		api.addMessageCreateListener(new BotInfo(0, "!bd botinfo", "Show bot information."));
		api.addMessageCreateListener(new Debug(1, "!bd debug", "Show debug information."));
	}
	
	public static String getBotOwner(MessageCreateEvent event) {
    	String bot_owner = "<@";
    	try {
			bot_owner += Long.toString(event.getApi().getApplicationInfo().get().getOwnerId());
			bot_owner += ">";
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return bot_owner;
	}
	
	// Sets the footer, done here to keep it standardised.
	public static void setFooter(EmbedBuilder embed) {
		embed.setFooter("Bungee Discord " + Main.inst().getDescription().getVersion().toString() + " | !bd", Constants.footerIconURL);
	}
}