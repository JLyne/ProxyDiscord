package me.prouser123.bungee.discord.commands;

import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.ChatMessages;
import me.prouser123.bungee.discord.VerificationManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class Unlink extends BaseCommand {
    private static LinkingManager linker = null;

    public Unlink() {
        super("unlink");

        Unlink.linker = Main.inst().getLinkingManager();
    }

    @Override
    public void execute(CommandSender commandSender, String[] strings) {
        if(linker.isLinked((ProxiedPlayer) commandSender)) {
            VerificationManager verificationManager = Main.inst().getVerificationManager();

            linker.unlink((ProxiedPlayer) commandSender);
            verificationManager.checkVerificationStatus((ProxiedPlayer) commandSender);
            commandSender.sendMessage(new TextComponent(ChatMessages.getMessage("unlink-success")));
        } else {
            commandSender.sendMessage(new TextComponent(ChatMessages.getMessage("unlink-unlink-not-linked")));
        }
    }
}
