package me.prouser123.bungee.discord.commands;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import me.prouser123.bungee.discord.ChatMessages;
import me.prouser123.bungee.discord.VerificationManager;
import net.kyori.text.TextComponent;

public class Unlink implements Command {
    private static LinkingManager linker = null;

    public Unlink() {
        Unlink.linker = Main.inst().getLinkingManager();
    }

    @Override
    public void execute(CommandSource commandSender, String[] strings) {
        if(linker.isLinked((Player) commandSender)) {
            VerificationManager verificationManager = Main.inst().getVerificationManager();

            linker.unlink((Player) commandSender);
            verificationManager.checkVerificationStatus((Player) commandSender);
            commandSender.sendMessage(TextComponent.of(ChatMessages.getMessage("unlink-success")));
        } else {
            commandSender.sendMessage(TextComponent.of(ChatMessages.getMessage("unlink-not-linked")));
        }
    }
}
