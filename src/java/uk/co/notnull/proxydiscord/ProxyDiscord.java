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
import uk.co.notnull.proxydiscord.bot.listeners.Reconnect;
import uk.co.notnull.proxydiscord.bot.listeners.ServerMemberBan;
import uk.co.notnull.proxydiscord.bot.listeners.ServerMemberJoin;
import uk.co.notnull.proxydiscord.bot.listeners.ServerMemberLeave;
import uk.co.notnull.proxydiscord.bot.listeners.UserRoleAdd;
import uk.co.notnull.proxydiscord.bot.listeners.UserRoleRemove;
import uk.co.notnull.proxydiscord.commands.Link;
import uk.co.notnull.proxydiscord.commands.Save;
import uk.co.notnull.proxydiscord.commands.Unlink;
import uk.co.notnull.proxydiscord.listeners.ProxyQueues;
import uk.co.notnull.proxydiscord.listeners.JoinLeave;
import uk.co.notnull.proxydiscord.listeners.SendStatus;
import uk.co.notnull.proxydiscord.listeners.ServerConnect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.io.ByteStreams;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;

import javax.inject.Inject;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import uk.co.notnull.proxydiscord.manager.*;

@Plugin(id = "proxydiscord", name = "ProxyDiscord", version = "1.0-SNAPSHOT",
        description = "Discord integrations", authors = {"Jim (NotKatuen)"}, dependencies = {
		@Dependency(id = "luckperms"),
		@Dependency(id = "proxyqueues", optional = true),
		@Dependency(id = "platform-detection", optional = true)
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
	private static AnnouncementManager announcementManager;
	private static RedirectManager redirectManager;
	private static LoggingManager loggingManager;
	private static LuckPermsManager luckPermsManager;

	private boolean platformDetectionEnabled = false;
	private Object platformDetection;

	@Inject
    @DataDirectory
    private Path dataDirectory;

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
		debugLogger = new DebugLogger(this);

		discord = new Discord(this, getConfig());

		Messages.setMessages(messagesConfiguration);

		luckPermsManager = new LuckPermsManager(this, getConfig());
		initLinking();
		initVerification();
		loggingManager = new LoggingManager(this, getConfig());
		announcementManager = new AnnouncementManager(this, getConfig().getNode("announcement-channels"));
		redirectManager = new RedirectManager(this);

		proxy.getEventManager().register(this, new JoinLeave(this));

		Optional<PluginContainer> proxyQueues = proxy.getPluginManager().getPlugin("proxyqueues");
        proxyQueues.flatMap(PluginContainer::getInstance).ifPresent(instance -> {
			proxy.getEventManager().register(this,
											 new ProxyQueues(this, (uk.co.notnull.proxyqueues.ProxyQueues) instance));
		});

        Optional<PluginContainer> platformDetection = proxy.getPluginManager()
                .getPlugin("platform-detection");
        platformDetectionEnabled = platformDetection.isPresent();

        if(platformDetectionEnabled) {
            this.platformDetection = platformDetection.get().getInstance().orElse(null);
        }

		VelocityCommandManager commandManager = new VelocityCommandManager(proxy, this);
		commandManager.registerCommand(new Link(this));
		commandManager.registerCommand(new Unlink(this));
		commandManager.registerCommand(new Save(this));
	}

	private void initLinking() {
		String linkingChannelId = getConfig().getNode("linking-channel-id").getString();
		String linkingSecret = getConfig().getNode("linking-secret").getString();

		linkingManager = new LinkingManager(this, linkingChannelId, linkingSecret);
	}

	private void initVerification() {
		verificationManager = new VerificationManager(this, getConfig());
		proxy.getEventManager().register(this, new ServerConnect(this));
		proxy.getEventManager().register(this, new SendStatus(this));

		discord.getApi().addUserRoleAddListener(new UserRoleAdd(this));
		discord.getApi().addUserRoleRemoveListener(new UserRoleRemove(this));
		discord.getApi().addServerMemberBanListener(new ServerMemberBan(this));
		discord.getApi().addServerMemberJoinListener(new ServerMemberJoin(this));
		discord.getApi().addServerMemberLeaveListener(new ServerMemberLeave(this));
		discord.getApi().addReconnectListener(new Reconnect(this));
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

	@SuppressWarnings("unused")
	public AnnouncementManager getAnnouncementManager() {
		return announcementManager;
	}

	@SuppressWarnings("unused")
	public RedirectManager getRedirectManager() {
		return redirectManager;
	}

	public LoggingManager getLoggingManager() {
		return loggingManager;
	}

	public LuckPermsManager getLuckpermsManager() {
		return luckPermsManager;
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

	public boolean isPlatformDetectionEnabled() {
		return platformDetectionEnabled;
	}

	public Object getPlatformDetection() {
		return platformDetection;
	}
}