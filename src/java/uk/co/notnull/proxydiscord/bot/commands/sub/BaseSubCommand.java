package uk.co.notnull.proxydiscord.bot.commands.sub;

import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.bot.commands.BaseCommand;
import uk.co.notnull.proxydiscord.bot.commands.MainCommand;

public interface BaseSubCommand extends BaseCommand {
	
	default void addCommandToHelp(base b) {
		ProxyDiscord.inst().getDebugLogger().info("[BaseSubCommand@Add2Help] Adding " + b.command);
		MainCommand.subArray.add(b.helpPriority, b.command + arraySeperator + b.helpText);
		ProxyDiscord.inst().getDebugLogger().info("[BaseSubCommand@Add2Help] " + MainCommand.subArray);
	}
}