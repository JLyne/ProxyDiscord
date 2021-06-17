package uk.co.notnull.proxydiscord.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.luckperms.api.model.user.UserManager;
import uk.co.notnull.proxydiscord.ChatMessages;
import uk.co.notnull.proxydiscord.LinkResult;
import uk.co.notnull.proxydiscord.LinkingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.javacord.api.entity.user.User;

import java.util.UUID;

@CommandAlias("discord")
public class Link extends BaseCommand {
    private static LinkingManager linkingManager = null;
    private static UserManager userManager = null;

    public Link() {
        Link.linkingManager = ProxyDiscord.inst().getLinkingManager();
        Link.userManager = ProxyDiscord.inst().getLuckpermsManager().getUserManager();
    }

    @Subcommand("link")
    @CommandAlias("link")
    @CommandCompletion("@players")
    @CommandPermission("discord.link")
    public void onLink(Player player, String target, String discordId) {
         //Mod Linking another player
        if (!player.hasPermission("discord.link.others")) {
            return;
        }

        //Missing discord id, return expected format
        if (discordId == null) {
            player.sendMessage(Identity.nil(), Component.text("Format: /link <player> <discordid>").color(NamedTextColor.RED));

            return;
        }

        try {
            Long.parseLong(discordId);
        } catch (NumberFormatException e) {
            player.sendMessage(Identity.nil(), Component.text("Discord ID " + discordId + " is invalid.").color(NamedTextColor.RED));
            return;
        }

        userManager.lookupUniqueId(target).thenAccept((UUID uuid) -> {
            if (uuid == null) {
                TextComponent.Builder playerMessage = Component.text()
                        .content(ChatMessages.getMessage("link-not-found")
                                         .replace("[player]", target))
                        .color(NamedTextColor.GREEN);

                player.sendMessage(Identity.nil(), playerMessage.build());

                return;
            }

            Long linkedDiscord = linkingManager.getLinked(uuid);
            UUID linkedMinecraft = linkingManager.getLinked(Long.parseLong(discordId));

            //Player is already a discord account
            if (linkedDiscord != null) {
                //Player has linked the same account
                if (linkedDiscord.toString().equals(discordId)) {
                    String message = ChatMessages.getMessage("link-other-already-linked-same")
                            .replace("[player]", target);
                    player.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.RED));
                } else {
                    //Attempt to get username of linked discord account
                    ProxyDiscord.inst().getDiscord().getApi().getUserById(linkedDiscord).thenAcceptAsync(user -> {
                        String message = ChatMessages.getMessage("link-other-already-linked-known")
                                .replace("[player]", target)
                                .replace("[discord]", user.getDiscriminatedName());

                        player.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.RED));
                    }).exceptionally(error -> {
                        String message = ChatMessages.getMessage("link-other-already-linked-unknown")
                                .replace("[player]", target);

                        player.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.RED));

                        return null;
                    });
                }

                return;
            }

            //Specified discord account is already to another player
            if (linkedMinecraft != null) {
                final User[] discordUser = {null};

                ProxyDiscord.inst().getDiscord().getApi().getUserById(discordId)
                        .thenComposeAsync(result -> {
                            discordUser[0] = result;
                            return userManager.lookupUsername(linkedMinecraft);
                        })
                        .thenAcceptAsync(minecraftUsername -> {
                            String discordUsername = discordUser[0] != null ? discordUser[0].getDiscriminatedName() : discordId;
                            String message;

                            if (minecraftUsername != null) {
                                message = ChatMessages.getMessage("link-other-discord-already-linked-known")
                                        .replace("[player]", minecraftUsername);
                            } else {
                                message = ChatMessages.getMessage("link-other-discord-already-linked-unknown");
                            }

                            message = message.replace("[discord]", discordUsername);

                            player.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.RED));
                        }).exceptionally(error -> {
                    player.sendMessage(Identity.nil(), Component.text(error.toString()).color(NamedTextColor.RED));
                    return null;
                });

                return;
            }

            //Attempt to link player
            ProxyDiscord.inst().getDiscord().getApi().getUserById(discordId).thenAcceptAsync(user -> {
                LinkResult result = linkingManager.manualLink(uuid, user.getId());

                ProxyDiscord.inst().getLogger().debug(result.toString());

                if (result == LinkResult.SUCCESS) {
                    String message = ChatMessages.getMessage("link-other-success")
                            .replace("[discord]", user.getDiscriminatedName())
                            .replace("[player]", target);

                    player.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.GREEN));
                } else if (result == LinkResult.NOT_VERIFIED) {
                    String message = ChatMessages.getMessage("link-other-not-verified")
                            .replace("[discord]", user.getDiscriminatedName())
                            .replace("[player]", target);

                    player.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.YELLOW));
                }
            }).exceptionally(error -> {
                player.sendMessage(Identity.nil(), Component.text(error.toString()).color(NamedTextColor.RED));
                return null;
            });
        });
    }
}
