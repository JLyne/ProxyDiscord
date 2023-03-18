/*
 * ProxyDiscord, a Velocity Discord bot
 * Copyright (c) 2021 James Lyne
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.model.user.UserManager;
import org.javacord.api.entity.user.User;
import uk.co.notnull.proxydiscord.api.LinkResult;
import uk.co.notnull.proxydiscord.api.VerificationResult;
import uk.co.notnull.proxydiscord.manager.LinkingManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    public void link(CommandSource sender, @Argument(value = "player", suggestions = "visibleplayers") String target,
                     @Argument(value = "discordId") @Range(min = "0") Long discordId) {

        userManager.lookupUniqueId(target).thenAccept((UUID uuid) -> {
            if (uuid == null) {
                Messages.sendComponent(sender, "link-other-not-found",
                                                                         Collections.singletonMap("player", target),
                                                                         Collections.emptyMap());

                return;
            }

            Long linkedDiscord = linkingManager.getLinked(uuid);
            UUID linkedMinecraft = linkingManager.getLinked(discordId);

            //Player is already a discord account
            if (linkedDiscord != null) {
                //Player has linked the same account
                if (linkedDiscord.equals(discordId)) {
                    Messages.sendComponent(sender, "link-other-already-linked-same",
                                                                              Collections.singletonMap("player", target),
                                                                              Collections.emptyMap());
                } else {
                    //Attempt to get username of linked discord account
                    plugin.getDiscord().getApi().getUserById(linkedDiscord)
                            .thenAcceptAsync(user -> Messages.sendComponent(
                                    sender,
                                    "link-other-already-linked-known",
                                    Map.of("player", target, "discord", user.getDiscriminatedName()),
                                    Collections.emptyMap()))
                            .exceptionally(error -> {
                                Messages.sendComponent(sender, "link-other-already-linked-unknown",
                                                       Collections.singletonMap("player", target),
                                                       Collections.emptyMap());

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

                            String key;
                            Map<String, String> replacements = new HashMap<>();
                            replacements.put("discord", discordUsername);

                            if (minecraftUsername != null) {
                                key = "link-other-discord-already-linked-known";
                                replacements.put("player", minecraftUsername);
                            } else {
                                key = "link-other-discord-already-linked-unknown";
                            }

                            Messages.sendComponent(sender, key, replacements, Collections.emptyMap());
                        }).exceptionally(error -> {
                    sender.sendMessage(Component.text(error.toString()).color(NamedTextColor.RED));
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
                    String key;

                    if (verificationResult.isVerified()) {
                        key = "link-other-success";
                    } else {
                        key = "link-other-not-verified";
                    }

                    Messages.sendComponent(sender, key, Map.of(
                            "discord", user.getDiscriminatedName(),
                            "player", target), Collections.emptyMap());
                }
            }).exceptionally(error -> {
                sender.sendMessage(Component.text(error.toString()).color(NamedTextColor.RED));
                return null;
            });
        });
    }

	@CommandMethod("discord unlink [player]")
    @ProxiedBy("unlink")
    @CommandPermission("discord.unlink")
    public void unlink(Player sender, @Argument(value = "player", suggestions = "visibleplayers") String target) {
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

                    Messages.sendComponent(sender, "unlink-other-discord-success",
                                                                             Collections.singletonMap("player", target),
                                                                             Collections.emptyMap());

                    if (onlinePlayer != null) {
                        Messages.sendComponent(onlinePlayer, "unlink-by-other-success",
                                               Collections.singletonMap("player", sender.getUsername()),
                                               Collections.emptyMap());
                    }
                } else {
                    Messages.sendComponent(sender, "unlink-other-discord-not-linked",
                                                                             Collections.singletonMap("player", target),
                                                                             Collections.emptyMap());
                }

                return;
            }

            userManager.lookupUniqueId(target).thenAccept((UUID uuid) -> {
                if(uuid == null) {
                    Messages.sendComponent(sender, "unlink-other-not-found",
                                                             Collections.singletonMap("player", target),
                                                             Collections.emptyMap());

                    return;
                }

                Player onlinePlayer = plugin.getProxy().getPlayer(uuid).orElse(null);

                if(linkingManager.isLinked(uuid)) {
                    linkingManager.unlink(uuid);

                    Messages.sendComponent(sender, "unlink-other-success",
                                           Collections.singletonMap("player", target),
                                           Collections.emptyMap());

                    if(onlinePlayer != null) {
                        Messages.sendComponent(onlinePlayer, "unlink-by-other-success",
                                               Collections.singletonMap("player", sender.getUsername()),
                                               Collections.emptyMap());
                    }
                } else {
                    Messages.sendComponent(sender, "unlink-other-not-linked",
                                                             Collections.singletonMap("player", target),
                                                             Collections.emptyMap());
                }
            });
        } else if(linkingManager.isLinked(sender)) { //Unlinking self
            linkingManager.unlink(sender);
        } else {
            Messages.sendComponent(sender, "unlink-not-linked");
        }
    }

    @CommandMethod("discord save")
    @CommandPermission("discord.save")
    public void save(CommandSource sender) {
        linkingManager.saveLinks();
        Messages.sendComponent(sender, "save-success");
    }

    @CommandMethod("discord reload")
    @CommandPermission("discord.admin")
    public void reload(CommandSource sender) {
        plugin.reload();
        Messages.sendComponent(sender, "reload-success");
    }

    @CommandMethod("discord refreshcommands")
    @CommandPermission("discord.admin")
    public void recreateCommands(CommandSource sender) {
        plugin.getDiscord().createSlashCommands(true)
                .thenAccept((unused) -> Messages.sendComponent(sender, "refresh-commands-success"))
                .exceptionally(e -> {
                    e.printStackTrace();
                    Messages.sendComponent(sender, "refresh-commands-error");
                    return null;
                });
    }
}
