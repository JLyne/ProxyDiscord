package uk.co.notnull.proxydiscord;

import cloud.commandframework.CommandManager;
import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.ProxiedBy;
import cloud.commandframework.annotations.specifier.Greedy;
import cloud.commandframework.annotations.specifier.Range;
import cloud.commandframework.minecraft.extras.MinecraftHelp;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.model.user.UserManager;
import org.javacord.api.entity.user.User;
import uk.co.notnull.proxydiscord.api.LinkResult;
import uk.co.notnull.proxydiscord.api.VerificationResult;
import uk.co.notnull.proxydiscord.manager.LinkingManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;

import java.util.UUID;

public class Commands {
    private final ProxyDiscord plugin;
    private final LinkingManager linkingManager;
    private final VerificationManager verificationManager;
    private final UserManager userManager;
    private final MinecraftHelp<CommandSource> minecraftHelp;

    public Commands(ProxyDiscord plugin, CommandManager<CommandSource> commandManager) {
        this.plugin = plugin;
        this.linkingManager = plugin.getLinkingManager();
        this.verificationManager = plugin.getVerificationManager();
        this.userManager = plugin.getLuckpermsManager().getUserManager();

        this.minecraftHelp = new MinecraftHelp<>("/discord", p -> p, commandManager);
    }

    @CommandMethod("discord help [query]")
    private void commandHelp(
            final CommandSource sender,
            final @Argument("query") @Greedy String query
    ) {
        this.minecraftHelp.queryCommands(query == null ? "" : query, sender);
    }

	@CommandMethod("discord link <player> <discordId>")
    @ProxiedBy("link")
    @CommandPermission("discord.link")
    public void link(CommandSource sender, @Argument(value = "player", suggestions = "players") String target,
                     @Argument(value = "discordId") @Range(min = "0") Long discordId) {

        userManager.lookupUniqueId(target).thenAccept((UUID uuid) -> {
            if (uuid == null) {
                TextComponent.Builder playerMessage = Component.text()
                        .content(Messages.get("link-not-found")
                                         .replace("[player]", target))
                        .color(NamedTextColor.GREEN);

                sender.sendMessage(Identity.nil(), playerMessage.build());

                return;
            }

            Long linkedDiscord = linkingManager.getLinked(uuid);
            UUID linkedMinecraft = linkingManager.getLinked(discordId);

            //Player is already a discord account
            if (linkedDiscord != null) {
                //Player has linked the same account
                if (linkedDiscord.equals(discordId)) {
                    String message = Messages.get("link-other-already-linked-same")
                            .replace("[player]", target);
                    sender.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.RED));
                } else {
                    //Attempt to get username of linked discord account
                    plugin.getDiscord().getApi().getUserById(linkedDiscord).thenAcceptAsync(user -> {
                        String message = Messages.get("link-other-already-linked-known")
                                .replace("[player]", target)
                                .replace("[discord]", user.getDiscriminatedName());

                        sender.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.RED));
                    }).exceptionally(error -> {
                        String message = Messages.get("link-other-already-linked-unknown")
                                .replace("[player]", target);

                        sender.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.RED));

                        return null;
                    });
                }

                return;
            }

            //Specified discord account is already to another player
            if (linkedMinecraft != null) {
                final User[] discordUser = {null};

                plugin.getDiscord().getApi().getUserById(discordId)
                        .thenComposeAsync(result -> {
                            discordUser[0] = result;
                            return userManager.lookupUsername(linkedMinecraft);
                        })
                        .thenAcceptAsync(minecraftUsername -> {
                            String discordUsername = discordUser[0] != null ? discordUser[0].getDiscriminatedName() : String.valueOf(
                                    discordId);
                            String message;

                            if (minecraftUsername != null) {
                                message = Messages.get("link-other-discord-already-linked-known")
                                        .replace("[player]", minecraftUsername);
                            } else {
                                message = Messages.get("link-other-discord-already-linked-unknown");
                            }

                            message = message.replace("[discord]", discordUsername);

                            sender.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.RED));
                        }).exceptionally(error -> {
                    sender.sendMessage(Identity.nil(), Component.text(error.toString()).color(NamedTextColor.RED));
                    return null;
                });

                return;
            }

            //Attempt to link player
            plugin.getDiscord().getApi().getUserById(discordId).thenAcceptAsync(user -> {
                LinkResult result = linkingManager.manualLink(uuid, user.getId());

                plugin.getLogger().debug(result.toString());

                if (result == LinkResult.SUCCESS) {
                    VerificationResult verificationResult = verificationManager.checkVerificationStatus(discordId);

                    if (verificationResult.isVerified()) {
                        String message = Messages.get("link-other-success")
                                .replace("[discord]", user.getDiscriminatedName())
                                .replace("[player]", target);

                        sender.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.GREEN));
                    } else {
                        String message = Messages.get("link-other-not-verified")
                                .replace("[discord]", user.getDiscriminatedName())
                                .replace("[player]", target);

                        sender.sendMessage(Identity.nil(), Component.text(message).color(NamedTextColor.YELLOW));
                    }
                }
            }).exceptionally(error -> {
                sender.sendMessage(Identity.nil(), Component.text(error.toString()).color(NamedTextColor.RED));
                return null;
            });
        });
    }

	@CommandMethod("discord unlink [player]")
    @ProxiedBy("unlink")
    @CommandPermission("discord.unlink")
    public void unlink(Player sender, @Argument(value = "player", suggestions = "players") String target) {
        //Unlinking another player
        if(target != null) {
            if(!sender.hasPermission("discord.unlink.others")) {
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
                    Player onlinePlayer = plugin.getProxy().getPlayer(uuid).orElse(null);
                    linkingManager.unlink(discordId);

                    TextComponent.Builder playerMessage = Component.text()
                           .content(Messages.get("unlink-other-discord-success")
                                            .replace("[player]", target))
                           .color(NamedTextColor.GREEN);

                    sender.sendMessage(Identity.nil(), playerMessage.build());

                    if(onlinePlayer != null) {
                        TextComponent.Builder targetMessage = Component.text()
                           .content(Messages.get("unlink-by-other-success")
                                            .replace("[player]", sender.getUsername()))
                           .color(NamedTextColor.YELLOW);

                        onlinePlayer.sendMessage(Identity.nil(), targetMessage.build());
                    }
                } else {
                    TextComponent.Builder playerMessage = Component.text()
                           .content(Messages.get("unlink-other-discord-not-linked")
                                            .replace("[player]", target))
                           .color(NamedTextColor.RED);

                    sender.sendMessage(Identity.nil(), playerMessage.build());
                }

                return;
            }

            userManager.lookupUniqueId(target).thenAccept((UUID uuid) -> {
                if(uuid == null) {
                    TextComponent.Builder playerMessage = Component.text()
                           .content(Messages.get("unlink-other-not-found")
                                            .replace("[player]", target))
                           .color(NamedTextColor.GREEN);

                    sender.sendMessage(Identity.nil(), playerMessage.build());

                    return;
                }

                Player onlinePlayer = plugin.getProxy().getPlayer(uuid).orElse(null);

                if(linkingManager.isLinked(uuid)) {
                    linkingManager.unlink(uuid);

                    TextComponent.Builder playerMessage = Component.text()
                           .content(Messages.get("unlink-other-success")
                                            .replace("[player]", target))
                           .color(NamedTextColor.GREEN);

                    sender.sendMessage(Identity.nil(), playerMessage.build());

                    if(onlinePlayer != null) {
                        TextComponent.Builder targetMessage = Component.text()
                           .content(Messages.get("unlink-by-other-success")
                                            .replace("[player]", sender.getUsername()))
                           .color(NamedTextColor.YELLOW);

                        onlinePlayer.sendMessage(Identity.nil(), targetMessage.build());
                    }
                } else {
                    TextComponent.Builder playerMessage = Component.text()
                           .content(Messages.get("unlink-other-not-linked")
                                            .replace("[player]", target))
                           .color(NamedTextColor.RED);

                    sender.sendMessage(Identity.nil(), playerMessage.build());
                }
            });
        } else if(linkingManager.isLinked(sender)) {
            linkingManager.unlink(sender);
        } else {
            sender.sendMessage(Identity.nil(), Component.text(
                    Messages.get("unlink-not-linked")).color(NamedTextColor.RED));
        }
    }

    @CommandMethod("discord save")
    @CommandPermission("discord.save")
    public void save(CommandSource sender) {
        linkingManager.saveLinks();
        sender.sendMessage(Identity.nil(), Component.text(Messages.get("save-success")));
    }

    @CommandMethod("discord reload")
    @CommandPermission("discord.reload")
    public void reload(CommandSource sender) {
        plugin.reload();
        sender.sendMessage(Identity.nil(), Component.text(Messages.get("reload-success")));
    }
}
