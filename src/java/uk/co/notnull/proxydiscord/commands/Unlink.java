package uk.co.notnull.proxydiscord.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.velocitypowered.api.proxy.Player;
import uk.co.notnull.proxydiscord.LinkingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.ChatMessages;
import uk.co.notnull.proxydiscord.VerificationManager;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import java.util.UUID;

@CommandAlias("discord")
public class Unlink extends BaseCommand {
    private static VerificationManager verificationManager = null;
    private static LinkingManager linker = null;

    public Unlink() {
        Unlink.linker = ProxyDiscord.inst().getLinkingManager();
        Unlink.verificationManager = ProxyDiscord.inst().getVerificationManager();
    }

    @Subcommand("unlink")
    @CommandAlias("unlink")
    @CommandCompletion("@players")
    @CommandPermission("discord.unlink")
    public void onUnlink(Player player, @Optional String target) {
        //Unlinking another player
        if(target != null) {
            if(!player.hasPermission("discord.unlink.others")) {
                return;
            }

            Long discordId = null;

            try {
                discordId = Long.parseLong(target);
            } catch (NumberFormatException ignored) {
            }

            if(discordId != null) {
                UUID uuid = linker.getLinked(discordId);

                if(uuid != null) {
                    Player onlinePlayer = ProxyDiscord.inst().getProxy().getPlayer(uuid).orElse(null);
                    linker.unlink(discordId);

                    TextComponent.Builder playerMessage = TextComponent.builder()
                           .content(ChatMessages.getMessage("unlink-other-discord-success")
                                            .replace("[player]", target))
                           .color(NamedTextColor.GREEN);

                    player.sendMessage(playerMessage.build());

                    if(onlinePlayer != null) {
                        TextComponent.Builder targetMessage = TextComponent.builder()
                           .content(ChatMessages.getMessage("unlink-by-other-success")
                                            .replace("[player]", player.getUsername()))
                           .color(NamedTextColor.YELLOW);

                        verificationManager.checkVerificationStatus(onlinePlayer);
                        onlinePlayer.sendMessage(targetMessage.build());
                    }
                } else {
                    TextComponent.Builder playerMessage = TextComponent.builder()
                           .content(ChatMessages.getMessage("unlink-other-discord-not-linked")
                                            .replace("[player]", target))
                           .color(NamedTextColor.RED);

                    player.sendMessage(playerMessage.build());
                }

                return;
            }

            LuckPerms luckPermsApi = LuckPermsProvider.get();

            luckPermsApi.getUserManager().lookupUniqueId(target).thenAccept((UUID uuid) -> {
                if(uuid == null) {
                    TextComponent.Builder playerMessage = TextComponent.builder()
                           .content(ChatMessages.getMessage("unlink-other-not-found")
                                            .replace("[player]", target))
                           .color(NamedTextColor.GREEN);

                    player.sendMessage(playerMessage.build());

                    return;
                }

                Player onlinePlayer = ProxyDiscord.inst().getProxy().getPlayer(uuid).orElse(null);

                if(linker.isLinked(uuid)) {
                    linker.unlink(uuid);

                    TextComponent.Builder playerMessage = TextComponent.builder()
                           .content(ChatMessages.getMessage("unlink-other-success")
                                            .replace("[player]", target))
                           .color(NamedTextColor.GREEN);

                    player.sendMessage(playerMessage.build());

                    if(onlinePlayer != null) {
                        TextComponent.Builder targetMessage = TextComponent.builder()
                           .content(ChatMessages.getMessage("unlink-by-other-success")
                                            .replace("[player]", player.getUsername()))
                           .color(NamedTextColor.YELLOW);

                        verificationManager.checkVerificationStatus(onlinePlayer);
                        onlinePlayer.sendMessage(targetMessage.build());
                    }
                } else {
                    TextComponent.Builder playerMessage = TextComponent.builder()
                           .content(ChatMessages.getMessage("unlink-other-not-linked")
                                            .replace("[player]", target))
                           .color(NamedTextColor.RED);

                    player.sendMessage(playerMessage.build());
                }
            });
        } else if(linker.isLinked(player)) {
            linker.unlink(player);
            verificationManager.checkVerificationStatus(player);
        } else {
            player.sendMessage(TextComponent.of(ChatMessages.getMessage("unlink-not-linked")).color(NamedTextColor.RED));
        }
    }
}
