package me.prouser123.bungee.discord.commands;

import me.prouser123.bungee.discord.ChatMessages;
import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;

public class Save extends BaseCommand {
    private static LinkingManager linker = null;

    public Save() {
        super("save");

        Save.linker = Main.inst().getLinkingManager();
    }

    @Override
    public void execute(CommandSender commandSender, String[] strings) {
        linker.saveLinks();
        commandSender.sendMessage(new TextComponent(ChatMessages.getMessage("save-success")));
    }
}
