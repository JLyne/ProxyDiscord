package me.prouser123.bungee.discord;

import co.aikar.commands.VelocityCommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.prouser123.bungee.discord.bot.commands.listeners.Reconnect;
import me.prouser123.bungee.discord.bot.commands.listeners.ServerMemberBan;
import me.prouser123.bungee.discord.bot.commands.listeners.UserRoleAdd;
import me.prouser123.bungee.discord.bot.commands.listeners.UserRoleRemove;
import me.prouser123.bungee.discord.commands.Link;
import me.prouser123.bungee.discord.commands.Save;
import me.prouser123.bungee.discord.commands.Unlink;
import me.prouser123.bungee.discord.listeners.ProxyQueues;
import me.prouser123.bungee.discord.listeners.JoinLeave;
import me.prouser123.bungee.discord.listeners.ServerConnect;

import java.io.*;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.io.ByteStreams;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;

import javax.inject.Inject;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;

@Plugin(id = "proxydiscord", name = "ProxyDiscord", version = "1.0-SNAPSHOT",
        description = "Discord account linking", authors = {"Jim (NotKatuen)"}, dependencies = {
		@Dependency(id = "luckperms"),
		@Dependency(id = "proxyqueues", optional = true)
})
public class ProxyDiscord {
	private static ProxyDiscord instance;

	private ConfigurationNode configuration;
	private ConfigurationNode messagesConfiguration;
	private ConfigurationNode botCommandConfiguration;
	private DebugLogger debugLogger;
	private Discord discord;

	private static LinkingManager linkingManager;
	private static VerificationManager verificationManager;
	private static KickManager kickManager;
	private static AnnouncementManager announcementManager;
	private static RedirectManager redirectManager;
	private static LoggingManager loggingManager;

	@Inject
    @DataDirectory
    private Path dataDirectory;

    public static ProxyDiscord inst() {
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

	public RedirectManager getRedirectManager() {
		return redirectManager;
	}

	public LoggingManager getLoggingManager() {
		return loggingManager;
	}

	private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public ProxyDiscord(ProxyServer proxy, Logger logger) {
    	this.proxy = proxy;
    	this.logger = logger;

		instance = this;
	}

	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) {
    	// Setup config
		loadResource("config.yml");
		try {
			configuration = YAMLConfigurationLoader.builder().setFile(
					new File(dataDirectory.toAbsolutePath().toString(), "config.yml")).build().load();
		} catch (IOException e) {
			logger.error("Error loading config.yml");
		}

		//Message config
		loadResource("messages.yml");
		try {
			messagesConfiguration = YAMLConfigurationLoader.builder().setFile(
					new File(dataDirectory.toAbsolutePath().toString(), "messages.yml")).build().load();
		} catch (IOException e) {
			logger.error("Error loading messages.yml");
		}

		// Setup bot config
		loadResource("bot-command-options.yml");
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
		redirectManager = new RedirectManager();

		proxy.getEventManager().register(this, new JoinLeave());

		Optional<PluginContainer> plugin = proxy.getPluginManager().getPlugin("proxyqueues");
        plugin.ifPresent(proxyQueues -> proxy.getEventManager().register(this, new ProxyQueues()));

		VelocityCommandManager commandManager = new VelocityCommandManager(proxy, this);
		commandManager.registerCommand(new Link());
		commandManager.registerCommand(new Unlink());
		commandManager.registerCommand(new Save());
	}

	private void initLinking() {
		String linkingUrl = getConfig().getNode("linking-url").getString();
		String linkingChannelId = getConfig().getNode("linking-channel-id").getString();

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
    	String loggingChannelId = getConfig().getNode("logging-channel-id").getString();

		loggingManager = new LoggingManager(loggingChannelId);
	}

	private void initAnnouncements() {
		String announcementChannelId = getConfig().getNode("announcement-channel-id").getString();

		announcementManager = new AnnouncementManager(announcementChannelId);
	}

	private void loadResource(String resource) {
    	File folder = dataDirectory.toFile();

        if(!folder.exists()) {
            folder.mkdir();
		}

        File resourceFile = new File(dataDirectory.toFile(), resource);

        try {
            if(!resourceFile.exists()) {
                resourceFile.createNewFile();

                try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
					 OutputStream out = new FileOutputStream(resourceFile)) {
                    ByteStreams.copy(in, out);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
	}

	@Subscribe
	public void onProxyShutdown(ProxyShutdownEvent event) {
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

	public Path getDataDirectory() {
		return dataDirectory;
	}
}