package me.prouser123.bungee.discord.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import co.aikar.commands.velocity.contexts.OnlinePlayer;
import com.velocitypowered.api.proxy.Player;
import me.prouser123.bungee.discord.ChatMessages;
import me.prouser123.bungee.discord.LinkResult;
import me.prouser123.bungee.discord.LinkingManager;
import me.prouser123.bungee.discord.ProxyDiscord;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.javacord.api.entity.user.User;

import java.util.UUID;

@CommandAlias("discord")
public class Link extends BaseCommand {
    private static LinkingManager linkingManager = null;

    public Link() {
        Link.linkingManager = ProxyDiscord.inst().getLinkingManager();
    }

    @Subcommand("link")
    @CommandAlias("link")
    @CommandCompletion("@players")
    @CommandPermission("discord.link")
    public void onJoin(Player player, @Optional OnlinePlayer target, @Optional String discordId) {
        //Mod lLinking another player
        if(target != null && player.hasPermission("discord.link.others")) {
            //Missing discord id, return expected format
            if(discordId == null) {
                player.sendMessage(TextComponent.of("Format: /link <player> <discordid>").color(TextColor.RED));

                return;
            }

            try {
                Long.parseLong(discordId);
            } catch(NumberFormatException e) {
                 player.sendMessage(TextComponent.of("Discord ID " + discordId + " is invalid.").color(TextColor.RED));
                 return;
            }

            Player targetPlayer = target.getPlayer();
            Long linkedDiscord = linkingManager.getLinked(targetPlayer);
            String linkedMinecraft = linkingManager.getLinked(Long.parseLong(discordId));

            //Player is already a discord account
            if(linkedDiscord != null) {
                ProxyDiscord.inst().getLogger().debug("linkedDiscord != null");

                //Player has linked the same account
                if(linkedDiscord.toString().equals(discordId)) {
                    String message = ChatMessages.getMessage("link-other-already-linked-same")
                            .replace("[player]", targetPlayer.getUsername());
                    player.sendMessage(TextComponent.of(message).color(TextColor.RED));
                } else {
                    //Attempt to get username of linked discord account
                    ProxyDiscord.inst().getDiscord().getApi().getUserById(linkedDiscord).thenAcceptAsync(user -> {
                        String message = ChatMessages.getMessage("link-other-already-linked-known")
                                .replace("[player]", targetPlayer.getUsername())
                                .replace("[discord]", user.getDiscriminatedName());

                        player.sendMessage(TextComponent.of(message).color(TextColor.RED));
                    }).exceptionally(error -> {
                       String message = ChatMessages.getMessage("link-other-already-linked-unknown")
                               .replace("[player]", targetPlayer.getUsername());

                       player.sendMessage(TextComponent.of(message).color(TextColor.RED));

                       return null;
                    });
                }

                return;
            }

            //Specified discord account is already to another player
            if(linkedMinecraft != null) {
                ProxyDiscord.inst().getLogger().debug("linkedMinecraft != null");

                LuckPerms luckPermsApi = LuckPermsProvider.get();
                final User[] discordUser = {null};

                ProxyDiscord.inst().getDiscord().getApi().getUserById(discordId)
                        .thenComposeAsync(result -> {
                            discordUser[0] = result;
                            return luckPermsApi.getUserManager().lookupUsername(UUID.fromString(linkedMinecraft));
                        })
                        .thenAcceptAsync(minecraftUsername -> {
                            String discordUsername = discordUser[0] != null ? discordUser[0].getDiscriminatedName() : discordId;
                            String message;

                            if(minecraftUsername != null) {
                                message = ChatMessages.getMessage("link-other-discord-already-linked-known")
                                        .replace("[player]", minecraftUsername);
                            } else {
                                message = ChatMessages.getMessage("link-other-discord-already-linked-unknown");
                            }

                            message = message.replace("[discord]", discordUsername);

                            player.sendMessage(TextComponent.of(message).color(TextColor.RED));
                        }).exceptionally(error -> {
                            player.sendMessage(TextComponent.of(error.toString()).color(TextColor.RED));
                            return null;
                        });

                return;
            }

            ProxyDiscord.inst().getLogger().debug("//Attempt to link player");

            //Attempt to link player
            ProxyDiscord.inst().getDiscord().getApi().getUserById(discordId).thenAcceptAsync(user -> {
                LinkResult result = linkingManager.manualLink(targetPlayer.getUniqueId(), user.getId());

                ProxyDiscord.inst().getLogger().debug(result.toString());

                if(result == LinkResult.SUCCESS) {
                    String message = ChatMessages.getMessage("link-other-success")
                            .replace("[discord]", user.getDiscriminatedName())
                            .replace("[player]", targetPlayer.getUsername());

                    player.sendMessage(TextComponent.of(message).color(TextColor.GREEN));
                } else if(result == LinkResult.NOT_VERIFIED) {
                    String message = ChatMessages.getMessage("link-other-not-verified")
                            .replace("[discord]", user.getDiscriminatedName())
                            .replace("[player]", targetPlayer.getUsername());

                    player.sendMessage(TextComponent.of(message).color(TextColor.YELLOW));
                }
            }).exceptionally(error -> {
                player.sendMessage(TextComponent.of(error.toString()).color(TextColor.RED));
                return null;
            });

            return;
        }

        linkingManager.startLink(player);
    }
}
