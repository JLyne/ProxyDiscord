package uk.co.notnull.proxydiscord;

import co.aikar.commands.VelocityCommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import uk.co.notnull.proxydiscord.bot.listeners.*;
import uk.co.notnull.proxydiscord.commands.Link;
import uk.co.notnull.proxydiscord.commands.Save;
import uk.co.notnull.proxydiscord.commands.Unlink;
import uk.co.notnull.proxydiscord.listeners.ProxyQueues;
import uk.co.notnull.proxydiscord.listeners.JoinLeave;
import uk.co.notnull.proxydiscord.listeners.SendStatus;
import uk.co.notnull.proxydiscord.listeners.ServerConnect;

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
		@Dependency(id = "proxyqueues", optional = true),
		@Dependency(id = "floodgate", optional = true)
})
public class ProxyDiscord {
	private static MinecraftChannelIdentifier statusIdentifier;
	private static ProxyDiscord instance;

	private ConfigurationNode configuration;
	private ConfigurationNode messagesConfiguration;
	private DebugLogger debugLogger;
	private Discord discord;

	private static LinkingManager linkingManager;
	private static VerificationManager verificationManager;
	private static KickManager kickManager;
	private static AnnouncementManager announcementManager;
	private static RedirectManager redirectManager;
	private static LoggingManager loggingManager;

	private boolean floodgateEnabled = false;

	@Inject
    @DataDirectory
    private Path dataDirectory;

    public static ProxyDiscord inst() {
    	  return instance;
    }

	public static MinecraftChannelIdentifier getStatusIdentifier() {
		return statusIdentifier;
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
		statusIdentifier = MinecraftChannelIdentifier.create("proxydiscord", "status");
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

		// Setup Debug Logging
		debugLogger = new DebugLogger();

		discord = new Discord(getConfig().getNode("token").getString());
		kickManager = new KickManager(getConfig().getNode("unverified-kick-time").getInt(120));

		new ChatMessages(messagesConfiguration);

		initActivityLogging();
		initLinking();
		initVerification();
		initAnnouncements();
		redirectManager = new RedirectManager();

		proxy.getEventManager().register(this, new JoinLeave());

		Optional<PluginContainer> proxyQueues = proxy.getPluginManager().getPlugin("proxyqueues");
        proxyQueues.flatMap(PluginContainer::getInstance).ifPresent(instance -> {
			proxy.getEventManager().register(this,
											 new ProxyQueues((uk.co.notnull.proxyqueues.ProxyQueues) instance));
		});

        Optional<PluginContainer> floodgate = proxy.getPluginManager().getPlugin("floodgate");
        floodgateEnabled = floodgate.isPresent();

		VelocityCommandManager commandManager = new VelocityCommandManager(proxy, this);
		commandManager.registerCommand(new Link());
		commandManager.registerCommand(new Unlink());
		commandManager.registerCommand(new Save());
	}

	private void initLinking() {
		String linkingChannelId = getConfig().getNode("linking-channel-id").getString();
		String linkingSecret = getConfig().getNode("linking-secret").getString();

		linkingManager = new LinkingManager(linkingChannelId, linkingSecret);
	}

	private void initVerification() {
		verificationManager = new VerificationManager(getConfig());
		proxy.getEventManager().register(this, new ServerConnect());
		proxy.getEventManager().register(this, new SendStatus());

		discord.getApi().addUserRoleAddListener(new UserRoleAdd());
		discord.getApi().addUserRoleRemoveListener(new UserRoleRemove());
		discord.getApi().addServerMemberBanListener(new ServerMemberBan());
		discord.getApi().addServerMemberJoinListener(new ServerMemberJoin());
		discord.getApi().addServerMemberLeaveListener(new ServerMemberLeave());
		discord.getApi().addReconnectListener(new Reconnect());
	}

	private void initActivityLogging() {
    	String loggingChannelId = getConfig().getNode("logging-channel-id").getString();

		loggingManager = new LoggingManager(loggingChannelId);
	}

	private void initAnnouncements() {
		ConfigurationNode announcementChannels = getConfig().getNode("announcement-channels");

		announcementManager = new AnnouncementManager(announcementChannels);
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

		if(discord.getApi() != null) {
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

	public boolean isFloodgateEnabled() {
		return floodgateEnabled;
	}
}