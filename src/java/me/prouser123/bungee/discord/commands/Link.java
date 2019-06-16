package me.prouser123.bungee.discord.commands;

import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class Link extends BaseCommand {
    private static LinkingManager linkingManager = null;

    public Link() {
        super("link");

        Link.linkingManager = Main.inst().getLinkingManager();
    }

    @Override
    public void execute(CommandSender commandSender, String[] strings) {
        linkingManager.startLink((ProxiedPlayer) commandSender);
    }
}
