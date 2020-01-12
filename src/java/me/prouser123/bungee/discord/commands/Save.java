package me.prouser123.bungee.discord.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import com.velocitypowered.api.proxy.Player;
import me.prouser123.bungee.discord.ChatMessages;
import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.ProxyDiscord;
import net.kyori.text.TextComponent;

public class Save extends BaseCommand {
    private static LinkingManager linker = null;

    public Save() {
        Save.linker = ProxyDiscord.inst().getLinkingManager();
    }

    @Subcommand("save")
    @CommandAlias("save")
    @CommandPermission("discord.save")
    public void onSave(Player player) {
        linker.saveLinks();
        player.sendMessage(TextComponent.of(ChatMessages.getMessage("save-success")));
    }
}
