/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2021 James Lyne
 *
 * Some portions of this file were taken from https://github.com/Prouser123/BungeeDiscord
 * These portions are Copyright (c) 2018 James Cahill
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package uk.co.notnull.proxydiscord;

import cloud.commandframework.CommandManager;
import cloud.commandframework.annotations.AnnotationParser;
import cloud.commandframework.arguments.parser.StandardParameters;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.meta.SimpleCommandMeta;
import cloud.commandframework.minecraft.extras.MinecraftExceptionHandler;
import cloud.commandframework.velocity.VelocityCommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.leangen.geantyref.TypeToken;
import uk.co.notnull.proxydiscord.bot.listeners.Reconnect;
import uk.co.notnull.proxydiscord.bot.listeners.ServerMemberBan;
import uk.co.notnull.proxydiscord.bot.listeners.ServerMemberJoin;
import uk.co.notnull.proxydiscord.bot.listeners.ServerMemberLeave;
import uk.co.notnull.proxydiscord.bot.listeners.UserRoleAdd;
import uk.co.notnull.proxydiscord.bot.listeners.UserRoleRemove;
import uk.co.notnull.proxydiscord.cloud.LongParser;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.io.ByteStreams;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;

import javax.inject.Inject;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import uk.co.notnull.proxydiscord.manager.*;

@Plugin(id = "proxydiscord", name = "ProxyDiscord", version = "1.0-SNAPSHOT",
        description = "Discord integrations", authors = {"Jim (NotKatuen)"}, dependencies = {
		@Dependency(id = "luckperms"),
		@Dependency(id = "platform-detection", optional = true)
})
public class ProxyDiscord implements uk.co.notnull.proxydiscord.api.ProxyDiscord {
	private static MinecraftChannelIdentifier statusIdentifier;
	private static ProxyDiscord instance;

	private ConfigurationNode configuration;
	private DebugLogger debugLogger;
	private Discord discord;

	private static LinkingManager linkingManager;
	private static VerificationManager verificationManager;
	private static AnnouncementManager announcementManager;
	private static RedirectManager redirectManager;
	private static LoggingManager loggingManager;
	private static LuckPermsManager luckPermsManager;
	private GroupSyncManager groupSyncManager;

	private boolean platformDetectionEnabled = false;
	private PlatformDetectionHandler platformDetectionHandler;

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
    	if(!loadConfig()) {
    		return;
		}

		debugLogger = new DebugLogger(this, configuration);

		discord = new Discord(this, configuration);

		luckPermsManager = new LuckPermsManager(this, configuration);
		linkingManager = new LinkingManager(this, configuration);
		verificationManager = new VerificationManager(this, configuration);
		groupSyncManager = new GroupSyncManager(this, configuration);
		loggingManager = new LoggingManager(this, configuration);
		announcementManager = new AnnouncementManager(this, configuration);
		redirectManager = new RedirectManager(this);

		initListeners();
        initCommands();

        Optional<PluginContainer> platformDetection = proxy.getPluginManager()
                .getPlugin("platform-detection");
        platformDetectionEnabled = platformDetection.isPresent();

        if(platformDetectionEnabled) {
            this.platformDetectionHandler = new PlatformDetectionHandler(this);
        }
	}

	private boolean loadConfig() {
		// Setup config
		loadResource("config.yml");
		try {
			configuration = YAMLConfigurationLoader.builder().setFile(
					new File(dataDirectory.toAbsolutePath().toString(), "config.yml")).build().load();
		} catch (IOException e) {
			logger.error("Error loading config.yml");
			e.printStackTrace();
			return false;
		}

		//Message config
		loadResource("messages.yml");
		ConfigurationNode messagesConfiguration;

		try {
			messagesConfiguration = YAMLConfigurationLoader.builder().setFile(
					new File(dataDirectory.toAbsolutePath().toString(), "messages.yml")).build().load();
		} catch (IOException e) {
			logger.error("Error loading messages.yml");
			e.printStackTrace();
			return false;
		}

		Messages.set(messagesConfiguration);

		return true;
	}

	private void initCommands() {
        CommandManager<CommandSource> manager = new VelocityCommandManager<>(
				proxy.getPluginManager().fromInstance(this).get(),
				proxy,
				CommandExecutionCoordinator.simpleCoordinator(),
				Function.identity(),
				Function.identity());

        new MinecraftExceptionHandler<CommandSource>()
            .withArgumentParsingHandler()
            .withInvalidSenderHandler()
            .withInvalidSyntaxHandler()
            .withNoPermissionHandler()
            .withCommandExecutionHandler()
            .withDecorator(message -> message)
            .apply(manager, p -> p);

        manager.getParserRegistry().registerSuggestionProvider("players", (
        		CommandContext<CommandSource> commandContext,
                String input
        ) -> commandContext.<ProxyServer>get("ProxyServer").getAllPlayers()
				.stream().map(Player::getUsername).collect(Collectors.toList()));

        manager.getParserRegistry().registerParserSupplier(TypeToken.get(Long.class), options ->
                new LongParser<>(
                        (long) options.get(StandardParameters.RANGE_MIN, Long.MIN_VALUE),
                        (long) options.get(StandardParameters.RANGE_MAX, Long.MAX_VALUE)
                ));

        AnnotationParser<CommandSource> annotationParser = new AnnotationParser<>(
                manager,
                CommandSource.class,
                parameters -> SimpleCommandMeta.empty()
        );

        annotationParser.parse(new Commands(this, manager));
    }

	private void initListeners() {
		proxy.getEventManager().register(this, new ServerConnect(this));
		proxy.getEventManager().register(this, new SendStatus(this));
		proxy.getEventManager().register(this, new JoinLeave(this));

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

	public GroupSyncManager getGroupSyncManager() {
		return groupSyncManager;
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

	public PlatformDetectionHandler getPlatformDetectionHandler() {
		return platformDetectionHandler;
	}

	public void reload() {
		if(loadConfig()) {
			debugLogger.reload(configuration);
			luckPermsManager.reload(configuration);
			linkingManager.reload(configuration);
			verificationManager.reload(configuration);
			groupSyncManager.reload(configuration);
			loggingManager.reload(configuration);
			announcementManager.reload(configuration);
		}
	}
}