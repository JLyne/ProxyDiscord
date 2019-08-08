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

import java.util.concurrent.CompletionException;

import org.javacord.api.DiscordApi;

public class Discord {
	/**
	 * Discord API Instance
	 */
	private DiscordApi api; // Api Instance

	private final Configuration commandConfiguration;

	private boolean connected = false;

	/**
	 * Class
	 * @param token bot token
	 */
	public Discord(String token, Configuration commandConfiguration) {
		// Bot token
		this.commandConfiguration = commandConfiguration;

		// Create an Instance of the DiscordApi
		try {
			Main.inst().getLogger().info("Connecting to Discord...");
			api = new DiscordApiBuilder().setToken(token).login().join();

			/*
			 *  FIXME: Seems to be a race condition here, which causes the various config channel/role ids to not be found
			 *  Which naturally breaks everything
			 *  Adding a sleep for now
			 */
			Main.inst().getLogger().info("Waiting a sec...");
			Thread.sleep(4000);

			connected = true;
		} catch (CompletionException IllegalStateException) {
			Main.inst().getLogger().warning("Connection Error. Did you put a valid token in the config?");
			return;
		} catch(InterruptedException ignored) {
		}

		// Print the invite url of the bot
        Main.inst().getLogger().info("Bot Invite Link: " + api.createBotInvite());

		// Cache a maximum of 10 messages per channel for and remove messages older than 1 hour
		api.setMessageCacheSize(10, 60*60);

		// Set Activity
        api.updateActivity(Constants.activity);

        api.addLostConnectionListener(event -> {
			connected = false;
			Main.inst().getLogger().info(("Lost connection to Discord."));
		});
        
        // Add Reconnect Listener to re-add status
        api.addReconnectListener(event -> {
			connected = true;
			Main.inst().getLogger().info(("Reconnected to Discord."));
			api.updateActivity(Constants.activity);
        });

		api.addResumeListener(event -> {
			connected = true;
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

	// Sets the footer, done here to keep it standardised.
	public static void setFooter(EmbedBuilder embed) {
		embed.setFooter("Bungee Discord " + Main.inst().getDescription().getVersion() + " | !bd", Constants.footerIconURL);
	}

	public DiscordApi getApi() {
		return api;
	}

	public Boolean isConnected() {
		return connected;
	}
}