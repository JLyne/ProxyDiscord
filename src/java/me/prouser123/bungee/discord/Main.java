package me.prouser123.bungee.discord;

import me.prouser123.bungee.discord.bot.commands.Link;
import me.prouser123.bungee.discord.bot.commands.MainCommand;
import me.prouser123.bungee.discord.bot.commands.Players;
import me.prouser123.bungee.discord.bot.commands.ServerInfo;
import me.prouser123.bungee.discord.bot.commands.listeners.UserRoleAdd;
import me.prouser123.bungee.discord.bot.commands.listeners.UserRoleRemove;
import me.prouser123.bungee.discord.listeners.JoinLeave;
import me.prouser123.bungee.discord.listeners.PlayerChat;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.*;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.google.common.io.ByteStreams;

// Since we need all the bot here, this is fine.
import me.prouser123.bungee.discord.bot.commands.sub.*;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.permission.Role;

public class Main extends Plugin {

	// Instancing
	private static Main instance;
	private static Configuration configuration;
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
    
    public static Configuration getConfigBotCommand() {
    	return botCommandConfiguration;
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
		// Instancing
		instance = this;
		
		getLogger().info("Welcome!");

		// Setup config
		loadResource(this, "config.yml");
		try {
			configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
		} catch (IOException e) {
			getLogger().severe("Error loading config.yml");
		}
		
		// Setup bot bot config
		loadResource(this, "bot-command-options.yml");
		try {
			botCommandConfiguration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "bot-command-options.yml"));
		} catch (IOException e) {
			getLogger().severe("Error loading bot-command-options.yml");
		}
		
		// Setup Debug Logging
		debugLogger = new DebugLogger();
		linkingManager = new LinkingManager();

        new Discord(getConfig().getString("token"));
        
        // Cache a maximum of 10 messages per channel for and remove messages older than 1 hour
        Discord.api.setMessageCacheSize(10, 60*60);

		Main.registerListeners.verification();

		Main.registerListeners.botCommands();
		Main.registerListeners.subCommands();

		Main.registerListeners.playerJoinLeave();
		Main.registerListeners.playerChat();

		getProxy().getPluginManager().registerCommand(this, new me.prouser123.bungee.discord.commands.Link());
		getProxy().getPluginManager().registerCommand(this, new me.prouser123.bungee.discord.commands.Unlink());
	}
	
	private static class registerListeners {
    	private static void verification() {
			String verifyRoleId = getConfig().getString("verify-role-id");
			String verifiedGroup = getConfig().getString("verified-permission");

			Optional<Role> verifyRole = Discord.api.getRoleById(verifyRoleId);

			if(!verifyRole.isPresent()) {
				Main.inst().getLogger().info("Role checking disabled. Did you put a valid role ID in the config?");
			} else {
				verificationManager = new VerificationManager(verifyRole.get(), verifiedGroup);
			}
		}
		
		private static void playerJoinLeave() {
			// Register Bungee Player Join/Leave Listeners
			String logChannelId = getConfig().getString("log-channel-id");
			Optional<TextChannel> logChannel = Discord.api.getTextChannelById(logChannelId);

			if(!logChannel.isPresent()) {
				Main.inst().getLogger().info("Join/Leave logging disabled. Did you put a valid channel ID in the config?");
			}

			Main.inst().getProxy().getPluginManager().registerListener(Main.inst(),  new JoinLeave(logChannel.orElse(null)));
			Main.inst().getLogger().info("Join Leave Chat enabled for channel: #" + logChannel.toString().replaceAll(".*\\[|\\].*", "") + " (id: " + logChannelId + ")");
		}

		private static void playerChat() {
			// Register Bungee Player Join/Leave Listeners
			String jlcID = getConfig().getString("log-channel-id");

			try {
				Main.inst().getProxy().getPluginManager().registerListener(Main.inst(), new PlayerChat(jlcID));
				Main.inst().getLogger().info("Player chat enabled for channel: #" + Discord.api.getChannelById(jlcID).toString().replaceAll(".*\\[|\\].*", "") + " (id: " + jlcID + ")");
			} catch (NoSuchElementException e) {
				Main.inst().getLogger().info("Player chat disabled. Did you put a valid channel ID in the config?");
			}
		}
		
		private static void botCommands() {
			Main.inst().getLogger().info("Registering bot...");
			
			// Register Main Command Class
			Discord.api.addMessageCreateListener(new MainCommand());
			
			// Attempt to register server-info from the config, falling back to the defaults.
			if (getConfigBotCommand().contains("server-info")) {
				Discord.api.addMessageCreateListener(new ServerInfo(0, getConfigBotCommand().getString("server-info.command"), getConfigBotCommand().getString("server-info.description")));
			} else {
				Main.inst().getLogger().warning("[Bot Command Options] Missing the server-info path. You will not be able to customize the !serverinfo command.");
				Discord.api.addMessageCreateListener(new ServerInfo(0, "!serverinfo", "Show server information."));
			}
			
			// Attempt to register players from the config, falling back to the defaults.
			if (getConfigBotCommand().contains("players")) {
				Discord.api.addMessageCreateListener(new Players(1, getConfigBotCommand().getString("players.command"), getConfigBotCommand().getString("players.description")));
			} else {
				Main.inst().getLogger().warning("[Bot Command Options] Missing the players path. You will not be able to customize the !players command.");
				Discord.api.addMessageCreateListener(new Players(1, "!players", "Show players currently on the network and their servers."));
			}

			Discord.api.addMessageCreateListener(new Link(2, "!link", "Allows players to link their discord account"));
		}
		
		private static void subCommands() {
			Main.inst().getLogger().info("Registering sub-bot...");

			Discord.api.addMessageCreateListener(new BotInfo(0, "!bd botinfo", "Show bot information."));
			Discord.api.addMessageCreateListener(new Debug(1, "!bd debug", "Show debug information."));
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