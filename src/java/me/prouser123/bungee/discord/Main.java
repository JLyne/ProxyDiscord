package me.prouser123.bungee.discord;

import me.prouser123.bungee.discord.commands.Link;
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
import org.javacord.api.entity.permission.Role;

public class Main extends Plugin {
	private static Main instance;
	private static Configuration configuration;
	private static Configuration messagesConfiguration;
	private static Configuration botCommandConfiguration;
	private static DebugLogger debugLogger;
	private static LinkingManager linkingManager;
	private static VerificationManager verificationManager;

    public static Main inst() {
    	  return instance;
    }

    public static Configuration getConfig() {
    	return configuration;
    }

    public DebugLogger getDebugLogger() {
    	return debugLogger;
    }

	public LinkingManager getLinkingManager() {
		return linkingManager;
	}

	public VerificationManager getVerificationManager() {
		return verificationManager;
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

		new ChatMessages(messagesConfiguration);
		
		// Setup Debug Logging
		debugLogger = new DebugLogger();

		String linkingUrl = getConfig().getString("linking-url");
		linkingManager = new LinkingManager(linkingUrl);

		new Discord(getConfig().getString("token"),  botCommandConfiguration);

		Main.registerListeners.verification();
		Main.registerListeners.activityLogging();

		getProxy().getPluginManager().registerCommand(this, new Link());
		getProxy().getPluginManager().registerCommand(this, new Unlink());
	}

	private static class registerListeners {
    	private static void verification() {
			String verifiedRoleId = getConfig().getString("verify-role-id");
			String verifiedPermission = getConfig().getString("verified-permission");
			String bypassPermission = getConfig().getString("bypass-permission");
			String unverifiedServerName = getConfig().getString("unverified-server");

			Optional<Role> verifiedRole = Discord.api.getRoleById(verifiedRoleId);
			net.md_5.bungee.api.config.ServerInfo unverifiedServer = Main.inst().getProxy().getServerInfo(unverifiedServerName);

			if(!verifiedRole.isPresent()) {
				Main.inst().getLogger().info("Role checking disabled. Did you put a valid role ID in the config?");
			} else {
				verificationManager = new VerificationManager(verifiedRole.get(), verifiedPermission, bypassPermission, unverifiedServer);
				Main.inst().getProxy().getPluginManager().registerListener(Main.inst(), new ServerConnect(unverifiedServer));
			}
		}
		
		private static void activityLogging() {
			String logChannelId = getConfig().getString("log-channel-id");
			Optional<TextChannel> logChannel = Discord.api.getTextChannelById(logChannelId);

			if(!logChannel.isPresent()) {
				Main.inst().getLogger().info("Activity logging disabled. Did you put a valid channel ID in the config?");
				return;
			}

			Main.inst().getProxy().getPluginManager().registerListener(Main.inst(), new PlayerChat(logChannel.get()));
			Main.inst().getProxy().getPluginManager().registerListener(Main.inst(), new JoinLeave(logChannel.get()));
			Main.inst().getLogger().info("Activity logging enabled for channel: #" + logChannel.toString().replaceAll(".*\\[|\\].*", "") + " (id: " + logChannelId + ")");
		}
	}

	public static File loadResource(Plugin plugin, String resource) {
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
        return resourceFile;
    }
	
	@Override
	public void onDisable() {
		if (Discord.api != null) {
			Discord.api.disconnect();
		}
	}
}