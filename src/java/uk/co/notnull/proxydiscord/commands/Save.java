package uk.co.notnull.proxydiscord.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.manager.LinkingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;

public class Save extends BaseCommand {
    private final LinkingManager linkingManager;

    public Save(ProxyDiscord plugin) {
        linkingManager = plugin.getLinkingManager();
    }

    @Subcommand("save")
    @CommandAlias("save")
    @CommandPermission("discord.save")
    public void onSave(Player player) {
        linkingManager.saveLinks();
        player.sendMessage(Identity.nil(), Component.text(Messages.getMessage("save-success")));
    }
}
