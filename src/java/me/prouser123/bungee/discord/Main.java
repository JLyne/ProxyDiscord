package me.prouser123.bungee.discord;

import com.google.common.eventbus.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
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

import java.io.*;
import java.nio.file.Path;

import com.google.common.io.ByteStreams;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;

import javax.inject.Inject;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;

@Plugin(id = "proxydiscord", name = "ProxyDiscord", version = "0.1-SNAPSHOT",
        description = "", authors = {"Jim (NotKatuen)"})
public class Main {
	private static Main instance;

	private ConfigurationNode configuration;
	private ConfigurationNode messagesConfiguration;
	private ConfigurationNode botCommandConfiguration;
	private DebugLogger debugLogger;
	private Discord discord;

	private static LinkingManager linkingManager;
	private static VerificationManager verificationManager;
	private static KickManager kickManager;
	private static AnnouncementManager announcementManager;
	private static LoggingManager loggingManager;

	@Inject
    @DataDirectory
    private Path dataDirectory;

    public static Main inst() {
    	  return instance;
    }

    ConfigurationNode getConfig() {
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

	private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public Main(ProxyServer proxy, Logger logger) {
    	this.proxy = proxy;
    	this.logger = logger;

		instance = this;
	}

	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) {
    	// Setup config
		loadResource(this, "config.yml");
		try {
			configuration = YAMLConfigurationLoader.builder().setFile(
					new File(dataDirectory.toAbsolutePath().toString(), "config.yml")).build().load();
		} catch (IOException e) {
			logger.error("Error loading config.yml");
		}

		//Message config
		loadResource(this, "messages.yml");
		try {
			messagesConfiguration = YAMLConfigurationLoader.builder().setFile(
					new File(dataDirectory.toAbsolutePath().toString(), "messages.yml")).build().load();
		} catch (IOException e) {
			logger.error("Error loading messages.yml");
		}

		// Setup bot config
		loadResource(this, "bot-command-options.yml");
		try {
			botCommandConfiguration = YAMLConfigurationLoader.builder().setFile(
					new File(dataDirectory.toAbsolutePath().toString(), "bot-command-options.yml")).build().load();
		} catch (IOException e) {
			logger.error("Error loading bot-command-options.yml");
		}

		// Setup Debug Logging
		debugLogger = new DebugLogger();

		discord = new Discord(getConfig().getNode("token").getString(),  botCommandConfiguration);
		kickManager = new KickManager(getConfig().getNode("unverified-kick-time").getInt(120));

		new ChatMessages(messagesConfiguration);

		initActivityLogging();
		initLinking();
		initVerification();
		initAnnouncements();

		proxy.getEventManager().register(this, new JoinLeave());
		proxy.getEventManager().register(this, new DeluxeQueues());

		proxy.getPluginManager().registerCommand(this, new Link());
		proxy.getPluginManager().registerCommand(this, new Unlink());
		proxy.getPluginManager().registerCommand(this, new Save());
	}

	private void initLinking() {
		String linkingUrl = getConfig().getString("linking-url");
		String linkingChannelId = getConfig().getString("linking-channel-id");

		linkingManager = new LinkingManager(linkingUrl, linkingChannelId);
	}

	private void initVerification() {
		verificationManager = new VerificationManager(getConfig());
		proxy.getEventManager().register(this, new ServerConnect());

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

	private static void loadResource(Main plugin, String resource) {
    	dataDirectory.resolve()
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

	public Logger getLogger() {
    	return logger;
	}

	public ProxyServer getProxy() {
    	return proxy;
	}
}