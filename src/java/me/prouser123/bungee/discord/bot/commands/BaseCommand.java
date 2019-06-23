package me.prouser123.bungee.discord.bot.commands;

import me.prouser123.bungee.discord.Main;

public interface BaseCommand {
	String arraySeperator = ":";
	
	default base createBase() {
		return new base();
	}
	
	// setup and return base with only one command
	default base easyBaseSetup(int priority, String command, String helpText) {
		base b = this.createBase();
		
		b.add(priority, command, helpText);
		debugInit(priority, command, helpText, b);
		addCommandToHelp(b);
		
		return b;
	}
		
	class base {
		public String command = "";
		public String helpText = "";
		public int helpPriority = 0;
		
		// Method to add variables from class init to base
		void add(int priority, String command, String helpText) {
			this.helpPriority = priority;
			this.command = command;
			this.helpText = helpText;
		}
	}
	
	default void addCommandToHelp(base b) {
		Main.inst().getDebugLogger().info("[BaseCommand@Add2Help] Adding " + b.command);
		MainCommand.array.add(b.helpPriority, b.command + arraySeperator + b.helpText);
		Main.inst().getDebugLogger().info("[BaseCommand@Add2Help] " + MainCommand.array);
	}
	
	// Command to dump information about help array
	default void debugInit(int priority, String command, String helpText, base base) {
		Main.inst().getDebugLogger().info("[BaseCommand@debugInit] Init info: " + priority + " | " + command + " | " + helpText);
		Main.inst().getDebugLogger().info("[BaseCommand@debugInit] BASE() info: | " + base.helpPriority + " | " + base.command + " | " + base.helpText);
	}
}