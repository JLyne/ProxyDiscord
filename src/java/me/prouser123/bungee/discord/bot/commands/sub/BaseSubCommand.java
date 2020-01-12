package me.prouser123.bungee.discord.bot.commands.sub;

import me.prouser123.bungee.discord.ProxyDiscord;
import me.prouser123.bungee.discord.bot.commands.BaseCommand;
import me.prouser123.bungee.discord.bot.commands.MainCommand;

public interface BaseSubCommand extends BaseCommand {
	
	default void addCommandToHelp(base b) {
		ProxyDiscord.inst().getDebugLogger().info("[BaseSubCommand@Add2Help] Adding " + b.command);
		MainCommand.subArray.add(b.helpPriority, b.command + arraySeperator + b.helpText);
		ProxyDiscord.inst().getDebugLogger().info("[BaseSubCommand@Add2Help] " + MainCommand.subArray);
	}
}