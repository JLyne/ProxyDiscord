package me.prouser123.bungee.discord;

import me.prouser123.bungee.discord.bot.commands.listeners.Reconnect;
import me.prouser123.bungee.discord.bot.commands.listeners.ServerMemberBan;
import me.prouser123.bungee.discord.bot.commands.listeners.UserRoleAdd;
import me.prouser123.bungee.discord.bot.commands.listeners.UserRoleRemove;
import me.prouser123.bungee.discord.commands.Link;
import me.prouser123.bungee.discord.commands.Save;
import me.prouser123.bungee.discord.commands.Unlink;
import me.prouser123.bungee.discord.listeners.DeluxeQueues;
import me.prouser123.bungee.discord.listeners.JoinLeave;
import me.prouser123.bungee.discord.listeners.ServerConnect;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.*;

import com.google.common.io.ByteStreams;

public class Main extends Plugin {
	private static Main instance;

	private Configuration configuration;
	private Configuration messagesConfiguration;
	private Configuration botCommandConfiguration;
	private DebugLogger debugLogger;
	private Discord discord;

	private static LinkingManager linkingManager;
	private static VerificationManager verificationManager;
	private static KickManager kickManager;
	private static AnnouncementManager announcementManager;
	private static LoggingManager loggingManager;

    public static Main inst() {
    	  return instance;
    }

    Configuration getConfig() {
    	return configuration;
    }

    public DebugLogger getDebugLogger() {
    	return debugLogger;
    }

	public Discord getDiscord() {
		return discord;
	}

	public LinkingManager getLinkingManager() {
		return linkingManager;
	}

	public VerificationManager getVerificationManager() {
		return verificationManager;
	}

	public KickManager getKickManager() {
		return kickManager;
	}

	public AnnouncementManager getAnnouncementManager() {
		return announcementManager;
	}

	public LoggingManager getLoggingManager() {
		return loggingManager;
	}

	@Override
	public void onEnable() {
		instance = this;

		getLogger().info("Welcome!");

		// Setup config
		loadResource(this, "config.yml");
		try {
			configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
		} catch (IOException e) {
			getLogger().severe("Error loading config.yml");
		}

		//Message config
		loadResource(this, "messages.yml");
		try {
			messagesConfiguration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "messages.yml"));
		} catch (IOException e) {
			getLogger().severe("Error loading messages.yml");
		}

		// Setup bot config
		loadResource(this, "bot-command-options.yml");
		try {
			botCommandConfiguration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "bot-command-options.yml"));
		} catch (IOException e) {
			getLogger().severe("Error loading bot-command-options.yml");
		}

		// Setup Debug Logging
		debugLogger = new DebugLogger();

		discord = new Discord(getConfig().getString("token"),  botCommandConfiguration);
		kickManager = new KickManager(getConfig().getInt("unverified-kick-time"));

		new ChatMessages(messagesConfiguration);

		initActivityLogging();
		initLinking();
		initVerification();
		initAnnouncements();

		getProxy().getPluginManager().registerListener(this, new JoinLeave());
		getProxy().getPluginManager().registerListener(this, new DeluxeQueues());

		getProxy().getPluginManager().registerCommand(this, new Link());
		getProxy().getPluginManager().registerCommand(this, new Unlink());
		getProxy().getPluginManager().registerCommand(this, new Save());
	}

	private void initLinking() {
		String linkingUrl = getConfig().getString("linking-url");
		String linkingChannelId = getConfig().getString("linking-channel-id");

		linkingManager = new LinkingManager(linkingUrl, linkingChannelId);
	}

	private void initVerification() {
		verificationManager = new VerificationManager(getConfig());
		getProxy().getPluginManager().registerListener(this, new ServerConnect());

		discord.getApi().addUserRoleAddListener(new UserRoleAdd());
		discord.getApi().addUserRoleRemoveListener(new UserRoleRemove());
		discord.getApi().addServerMemberBanListener(new ServerMemberBan());
		discord.getApi().addReconnectListener(new Reconnect());
	}

	private void initActivityLogging() {
    	String loggingChannelId = getConfig().getString("logging-channel-id");

		loggingManager = new LoggingManager(loggingChannelId);
	}

	private void initAnnouncements() {
		String announcementChannelId = getConfig().getString("announcement-channel-id");

		announcementManager = new AnnouncementManager(announcementChannelId);
	}

	private static void loadResource(Plugin plugin, String resource) {
        File folder = plugin.getDataFolder();
        if (!folder.exists())
            folder.mkdir();
        File resourceFile = new File(folder, resource);
        try {
            if (!resourceFile.exists()) {
                resourceFile.createNewFile();
                try (InputStream in = plugin.getResourceAsStream(resource);
                     OutputStream out = new FileOutputStream(resourceFile)) {
                    ByteStreams.copy(in, out);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	@Override
	public void onDisable() {
    	linkingManager.saveLinks();

		if (discord.getApi() != null) {
			discord.getApi().disconnect();
		}
	}
}