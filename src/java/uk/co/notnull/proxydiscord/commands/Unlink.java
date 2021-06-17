package uk.co.notnull.proxydiscord.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.luckperms.api.model.user.UserManager;
import uk.co.notnull.proxydiscord.LinkingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.ChatMessages;
import uk.co.notnull.proxydiscord.VerificationManager;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.UUID;

@CommandAlias("discord")
public class Unlink extends BaseCommand {
    private static UserManager userManager = null;
    private static VerificationManager verificationManager = null;
    private static LinkingManager linkingManager = null;

    public Unlink() {
        Unlink.linkingManager = ProxyDiscord.inst().getLinkingManager();
        Unlink.verificationManager = ProxyDiscord.inst().getVerificationManager();
        Unlink.userManager = ProxyDiscord.inst().getLuckpermsManager().getUserManager();
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
                UUID uuid = linkingManager.getLinked(discordId);

                if(uuid != null) {
                    Player onlinePlayer = ProxyDiscord.inst().getProxy().getPlayer(uuid).orElse(null);
                    linkingManager.unlink(discordId);

                    TextComponent.Builder playerMessage = Component.text()
                           .content(ChatMessages.getMessage("unlink-other-discord-success")
                                            .replace("[player]", target))
                           .color(NamedTextColor.GREEN);

                    player.sendMessage(Identity.nil(), playerMessage.build());

                    if(onlinePlayer != null) {
                        TextComponent.Builder targetMessage = Component.text()
                           .content(ChatMessages.getMessage("unlink-by-other-success")
                                            .replace("[player]", player.getUsername()))
                           .color(NamedTextColor.YELLOW);

                        verificationManager.checkVerificationStatus(onlinePlayer);
                        onlinePlayer.sendMessage(Identity.nil(), targetMessage.build());
                    }
                } else {
                    TextComponent.Builder playerMessage = Component.text()
                           .content(ChatMessages.getMessage("unlink-other-discord-not-linked")
                                            .replace("[player]", target))
                           .color(NamedTextColor.RED);

                    player.sendMessage(Identity.nil(), playerMessage.build());
                }

                return;
            }

            userManager.lookupUniqueId(target).thenAccept((UUID uuid) -> {
                if(uuid == null) {
                    TextComponent.Builder playerMessage = Component.text()
                           .content(ChatMessages.getMessage("unlink-other-not-found")
                                            .replace("[player]", target))
                           .color(NamedTextColor.GREEN);

                    player.sendMessage(Identity.nil(), playerMessage.build());

                    return;
                }

                Player onlinePlayer = ProxyDiscord.inst().getProxy().getPlayer(uuid).orElse(null);

                if(linkingManager.isLinked(uuid)) {
                    linkingManager.unlink(uuid);

                    TextComponent.Builder playerMessage = Component.text()
                           .content(ChatMessages.getMessage("unlink-other-success")
                                            .replace("[player]", target))
                           .color(NamedTextColor.GREEN);

                    player.sendMessage(Identity.nil(), playerMessage.build());

                    if(onlinePlayer != null) {
                        TextComponent.Builder targetMessage = Component.text()
                           .content(ChatMessages.getMessage("unlink-by-other-success")
                                            .replace("[player]", player.getUsername()))
                           .color(NamedTextColor.YELLOW);

                        verificationManager.checkVerificationStatus(onlinePlayer);
                        onlinePlayer.sendMessage(Identity.nil(), targetMessage.build());
                    }
                } else {
                    TextComponent.Builder playerMessage = Component.text()
                           .content(ChatMessages.getMessage("unlink-other-not-linked")
                                            .replace("[player]", target))
                           .color(NamedTextColor.RED);

                    player.sendMessage(Identity.nil(), playerMessage.build());
                }
            });
        } else if(linkingManager.isLinked(player)) {
            linkingManager.unlink(player);
            verificationManager.checkVerificationStatus(player);
        } else {
            player.sendMessage(Identity.nil(), Component.text(ChatMessages.getMessage("unlink-not-linked")).color(NamedTextColor.RED));
        }
    }
}
