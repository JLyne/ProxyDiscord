package me.prouser123.bungee.discord;

import me.prouser123.bungee.discord.bot.commands.listeners.Reconnect;
import me.prouser123.bungee.discord.bot.commands.listeners.UserRoleAdd;
import me.prouser123.bungee.discord.bot.commands.listeners.UserRoleRemove;
import me.prouser123.bungee.discord.commands.Link;
import me.prouser123.bungee.discord.commands.Save;
import me.prouser123.bungee.discord.commands.Unlink;
import me.prouser123.bungee.discord.listeners.JoinLeave;
import me.prouser123.bungee.discord.listeners.PlayerChat;
import me.prouser123.bungee.discord.listeners.ServerConnect;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.*;
import java.util.Optional;

import com.google.common.io.ByteStreams;

import org.javacord.api.entity.channel.TextChannel;

public class Main extends Plugin {
	private static Main instance;

	private Configuration configuration;
	private Configuration messagesConfiguration;
	private Configuration botCommandConfiguration;
	private DebugLogger debugLogger;
	private Discord discord;

	private LinkingManager linkingManager;
	private VerificationManager verificationManager;
	private KickManager kickManager;

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

		initLinking();
		discord = new Discord(getConfig().getString("token"),  botCommandConfiguration);
		kickManager = new KickManager(getConfig().getInt("unverified-kick-time"));

		new ChatMessages(messagesConfiguration);

		initVerification();
		initActivityLogging();

		getProxy().getPluginManager().registerCommand(this, new Link());
		getProxy().getPluginManager().registerCommand(this, new Unlink());
		getProxy().getPluginManager().registerCommand(this, new Save());
	}

	private void initLinking() {
		String linkingUrl = getConfig().getString("linking-url");
		linkingManager = new LinkingManager(linkingUrl);
	}

	private void initVerification() {
		verificationManager = new VerificationManager(getConfig());
		getProxy().getPluginManager().registerListener(this, new ServerConnect());

		discord.getApi().addUserRoleAddListener(new UserRoleAdd());
		discord.getApi().addUserRoleRemoveListener(new UserRoleRemove());
		discord.getApi().addReconnectListener(new Reconnect());
	}

	private void initActivityLogging() {
		String logChannelId = getConfig().getString("log-channel-id");
		Optional<TextChannel> logChannel = discord.getApi().getTextChannelById(logChannelId);

		if(!logChannel.isPresent()) {
			Main.inst().getLogger().info("Activity logging disabled. Did you put a valid channel ID in the config?");
			return;
		}

		getProxy().getPluginManager().registerListener(this, new PlayerChat(logChannel.get()));
		getProxy().getPluginManager().registerListener(this, new JoinLeave(logChannel.get()));
		getLogger().info("Activity logging enabled for channel: #" + logChannel.toString().replaceAll(".*\\[|].*", "") + " (id: " + logChannelId + ")");
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