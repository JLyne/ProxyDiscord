package uk.co.notnull.proxydiscord;

import com.velocitypowered.api.proxy.ProxyServer;
import org.javacord.api.entity.intent.Intent;
import uk.co.notnull.proxydiscord.bot.commands.Link;
import uk.co.notnull.proxydiscord.bot.commands.MainCommand;
import uk.co.notnull.proxydiscord.bot.commands.ServerInfo;
import uk.co.notnull.proxydiscord.bot.commands.sub.BotInfo;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.util.concurrent.CompletionException;

import org.javacord.api.DiscordApi;
import org.slf4j.Logger;

public class Discord {
	/**
	 * Discord API Instance
	 */
	private DiscordApi api; // Api Instance

	private boolean connected = false;

	private final ProxyServer proxy;
    private final Logger logger;

	/**
	 * Class
	 * @param token bot token
	 */
	public Discord(String token) {
		this.proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();
		// Bot token

		// Create an Instance of the DiscordApi
		try {
			logger.info("Connecting to Discord...");
			api = new DiscordApiBuilder().setToken(token)
					.setIntents(Intent.GUILDS, Intent.GUILD_MEMBERS, Intent.GUILD_MESSAGES, Intent.GUILD_PRESENCES)
					.setWaitForUsersOnStartup(true)
					.setWaitForServersOnStartup(true)
					.login().join();

			connected = true;
		} catch (CompletionException IllegalStateException) {
			logger.warn("Connection Error. Did you put a valid token in the config?");
			return;
		}

		// Print the invite url of the bot
        logger.info("Bot Invite Link: " + api.createBotInvite());

		// Cache a maximum of 10 messages per channel for and remove messages older than 1 hour
		api.setMessageCacheSize(10, 60*60);

		// Set Activity
        api.updateActivity(Constants.activity);

        api.addLostConnectionListener(event -> {
			connected = false;
			logger.info(("Lost connection to Discord."));
		});
        
        // Add Reconnect Listener to re-add status
        api.addReconnectListener(event -> {
			connected = true;
			logger.info(("Reconnected to Discord."));
			api.updateActivity(Constants.activity);
        });

		api.addResumeListener(event -> {
			connected = true;
			logger.info(("Resumed connection to Discord."));
			api.updateActivity(Constants.activity);
		});

        registerCommands();
        registerSubCommands();
	}

	private void registerCommands() {
		logger.info("Registering commands...");

		// Register Main Command Class
		api.addMessageCreateListener(new MainCommand());
		api.addMessageCreateListener(new Link(0, "!link", "Allows players to link their discord account"));
	}

	private void registerSubCommands() {
		api.addMessageCreateListener(new BotInfo(0, "!pd botinfo", "Show bot information."));
		api.addMessageCreateListener(new ServerInfo(1, "!pd serverinfo", "Show server information."));
	}

	// Sets the footer, done here to keep it standardised.
	public static void setFooter(EmbedBuilder embed) {
		embed.setFooter("Proxy Discord | !pd", Constants.footerIconURL);
	}

	public DiscordApi getApi() {
		return api;
	}

	public Boolean isConnected() {
		return connected;
	}
}