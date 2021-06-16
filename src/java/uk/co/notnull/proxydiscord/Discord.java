package uk.co.notnull.proxydiscord;

import org.javacord.api.entity.intent.Intent;
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

	private final Logger logger;

	/**
	 * Class
	 * @param token bot token
	 */
	public Discord(String token) {
		this.logger = ProxyDiscord.inst().getLogger();

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
	}

	public DiscordApi getApi() {
		return api;
	}

	public Boolean isConnected() {
		return connected;
	}
}