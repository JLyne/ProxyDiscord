package me.prouser123.bungee.discord.commands;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import me.prouser123.bungee.discord.ChatMessages;
import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import net.kyori.text.TextComponent;
import org.checkerframework.checker.nullness.qual.NonNull;

public class Save implements Command {
    private static LinkingManager linker = null;

    public Save() {
        Save.linker = Main.inst().getLinkingManager();
    }

    @Override
    public void execute(@NonNull CommandSource source, String[] args) {
        linker.saveLinks();
        source.sendMessage(TextComponent.of(ChatMessages.getMessage("save-success")));
    }
}
