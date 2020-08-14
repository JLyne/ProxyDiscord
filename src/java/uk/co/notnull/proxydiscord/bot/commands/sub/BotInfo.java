package uk.co.notnull.proxydiscord.bot.commands.sub;

import com.velocitypowered.api.proxy.ProxyServer;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;


import org.javacord.api.Javacord;

import uk.co.notnull.proxydiscord.Discord;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.Constants;
import org.slf4j.Logger;

public class BotInfo implements MessageCreateListener, BaseSubCommand {
	
	private final base base;

	private final ProxyServer proxy;
    private final Logger logger;

    public BotInfo(int priority, String command, String helpText) {
		this.proxy = ProxyDiscord.inst().getProxy();
		this.logger = ProxyDiscord.inst().getLogger();
		base = this.easyBaseSetup(priority, command, helpText);
	}
	
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        if (event.getMessage().getContent().equalsIgnoreCase(base.command)) {
			DiscordApi api = ProxyDiscord.inst().getDiscord().getApi();
        	
        	Object[] currentRoles = api.getYourself().getRoles(event.getServer().get()).toArray();

        	StringBuilder roles = new StringBuilder();
        	for (Object roleObject : currentRoles) {
        		String role = roleObject.toString().split(", name: ")[1].split(", server:")[0];
        		
        		// Remove the initial @ from the role name to avoid role spam
        		if (role.startsWith(("@"))) {
        			ProxyDiscord.inst().getDebugLogger().info("StartsWith @ - Removing...");
        			role = role.replace("@", "");
        		}
        		
        		// Adds to the list of roles
        		roles.append(role).append(" ");
        		ProxyDiscord.inst().getDebugLogger().info("Has role: " + role);
        	}
        	
        	EmbedBuilder embed = new EmbedBuilder()
        		.setAuthor("ProxyDiscord Bot Information", Constants.url, Constants.authorIconURL)
        		.addInlineField("ProxyDiscord Version", proxy.getPluginManager().fromInstance(ProxyDiscord.inst()).get().getDescription().getVersion().get())
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