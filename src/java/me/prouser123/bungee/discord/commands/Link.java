package me.prouser123.bungee.discord.commands;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.Main;
import org.checkerframework.checker.nullness.qual.NonNull;

public class Link implements Command {
    private static LinkingManager linkingManager = null;

    public Link() {
        Link.linkingManager = Main.inst().getLinkingManager();
    }

    @Override
    public void execute(@NonNull CommandSource source, String[] args) {
        if(source instanceof Player) {
            linkingManager.startLink((Player) source);
        }
    }
}
