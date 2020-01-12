package me.prouser123.bungee.discord.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import co.aikar.commands.velocity.contexts.OnlinePlayer;
import com.velocitypowered.api.proxy.Player;
import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.ProxyDiscord;
import me.prouser123.bungee.discord.ChatMessages;
import me.prouser123.bungee.discord.VerificationManager;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;

@CommandAlias("discord")
public class Unlink extends BaseCommand {
    private static LinkingManager linker = null;

    public Unlink() {
        Unlink.linker = ProxyDiscord.inst().getLinkingManager();
    }

    @Subcommand("unlink")
    @CommandAlias("unlink")
    @CommandCompletion("@players")
    @CommandPermission("discord.unlink")
    public void onUnlink(Player player, @Optional OnlinePlayer target) {
        //Unlinking another player
        if(target != null && player.hasPermission("discord.unlink.others")) {
            Player targetPlayer = target.getPlayer();

            if(linker.isLinked(targetPlayer)) {
                VerificationManager verificationManager = ProxyDiscord.inst().getVerificationManager();

                linker.unlink(targetPlayer);
                verificationManager.checkVerificationStatus(targetPlayer);

                TextComponent.Builder playerMessage = TextComponent.builder()
                       .content(ChatMessages.getMessage("unlink-other-success")
                                        .replace("[player]", targetPlayer.getUsername()))
                       .color(TextColor.GREEN);

                TextComponent.Builder targetUsername = TextComponent.builder()
                       .content(ChatMessages.getMessage("unlink-by-other-success")
                                        .replace("[player]", player.getUsername()))
                       .color(TextColor.YELLOW);

                player.sendMessage(playerMessage.build());
                targetPlayer.sendMessage(targetUsername.build());
            } else {
                TextComponent.Builder playerMessage = TextComponent.builder()
                       .content(ChatMessages.getMessage("unlink-other-not-linked")
                                        .replace("[player]", targetPlayer.getUsername()))
                       .color(TextColor.RED);

                player.sendMessage(playerMessage.build());
            }

           return;
        }

        if(linker.isLinked(player)) {
            VerificationManager verificationManager = ProxyDiscord.inst().getVerificationManager();

            linker.unlink(player);
            verificationManager.checkVerificationStatus(player);
            player.sendMessage(TextComponent.of(ChatMessages.getMessage("unlink-success")).color(TextColor.GREEN));
        } else {
            player.sendMessage(TextComponent.of(ChatMessages.getMessage("unlink-not-linked")).color(TextColor.RED));
        }
    }
}
