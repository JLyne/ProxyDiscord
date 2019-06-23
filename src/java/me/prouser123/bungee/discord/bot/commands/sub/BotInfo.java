package me.prouser123.bungee.discord.bot.commands.sub;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;


import org.javacord.api.Javacord;

import me.prouser123.bungee.discord.Discord;
import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.Constants;

public class BotInfo implements MessageCreateListener, BaseSubCommand {
	
	private final base base;
	
	public BotInfo(int priority, String command, String helpText) {
		base = this.easyBaseSetup(priority, command, helpText);
	}
	
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        if (event.getMessage().getContent().equalsIgnoreCase(base.command)) {
			DiscordApi api = Main.inst().getDiscord().getApi();
        	
        	Object[] currentRoles = api.getYourself().getRoles(event.getServer().get()).toArray();

        	StringBuilder roles = new StringBuilder();
        	for (Object roleObject : currentRoles) {
        		String role = roleObject.toString().split(", name: ")[1].split(", server:")[0];
        		
        		// Remove the initial @ from the role name to avoid role spam
        		if (role.startsWith(("@"))) {
        			Main.inst().getDebugLogger().info("StartsWith @ - Removing...");
        			role = role.replace("@", "");
        		}
        		
        		// Adds to the list of roles
        		roles.append(role).append(" ");
        		Main.inst().getDebugLogger().info("Has role: " + role);
        	}
        	
        	EmbedBuilder embed = new EmbedBuilder()
        		.setAuthor("BungeeDiscord Bot Information", Constants.url, Constants.authorIconURL)
        		.addInlineField("BungeeDiscord Version", Main.inst().getDescription().getVersion())
            	.addInlineField("JavaCord Version", Javacord.VERSION + " (API v" + Javacord.DISCORD_API_VERSION + ")")
            	.addInlineField("Bot Servers", Long.toString(api.getServers().size()))
            	.addInlineField("User Type", api.getAccountType().toString())
            	.addInlineField("Roles (Current Server)", roles.toString());
            
        	// Set footer
        	Discord.setFooter(embed);
            
        	// Send the embed
            event.getChannel().sendMessage(embed);
        }
    }
}