package me.prouser123.bungee.discord.commands;

import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.exceptions.AlreadyLinkedException;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class Link extends BaseCommand {
    private static LinkingManager linker = null;

    public Link() {
        super("link");

        Link.linker = Main.inst().getLinkingManager();
    }

    @Override
    public void execute(CommandSender commandSender, String[] strings) {
        try {
            String token = linker.startLink((ProxiedPlayer) commandSender);
            commandSender.sendMessage(new TextComponent("To verify click here https://minecraft.rtgame.co.uk/verify.php?" + token));
        } catch (AlreadyLinkedException e) {
            commandSender.sendMessage(new TextComponent("You have already linked a discord account. Type /unlink to unlink it."));
        }
    }
}
