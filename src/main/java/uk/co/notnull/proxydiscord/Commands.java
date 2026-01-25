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

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.entities.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.model.user.UserManager;
import uk.co.notnull.proxydiscord.api.LinkResult;
import uk.co.notnull.proxydiscord.api.VerificationResult;
import uk.co.notnull.proxydiscord.manager.LinkingManager;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import uk.co.notnull.vanishbridge.helper.VanishBridgeHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.velocitypowered.api.command.BrigadierCommand.literalArgumentBuilder;
import static com.velocitypowered.api.command.BrigadierCommand.requiredArgumentBuilder;

public class Commands {
    private final ProxyDiscord plugin;
    private final LinkingManager linkingManager;
    private final VerificationManager verificationManager;
    private final UserManager userManager;
    private static final SuggestionProvider<CommandSource> playerSuggestions =
            (ctx, builder) -> {
        VanishBridgeHelper.getInstance()
                .getUsernameSuggestions(builder.getRemainingLowerCase(), ctx.getSource())
                .forEach(builder::suggest);

        return builder.buildFuture();
    };

    public Commands(ProxyDiscord plugin) {
        this.plugin = plugin;
        this.linkingManager = plugin.getLinkingManager();
        this.verificationManager = plugin.getVerificationManager();
        this.userManager = plugin.getLuckpermsManager().getUserManager();
    }

    public void createCommands() {
        LiteralCommandNode<CommandSource> topNode = literalArgumentBuilder("discord")
                .then(literalArgumentBuilder("save")
                              .requires(source -> source.hasPermission("discord.save"))
                              .executes(context -> save(context.getSource()))
                )
                .then(literalArgumentBuilder("reload")
                              .requires(source -> source.hasPermission("discord.admin"))
                              .executes(context -> reload(context.getSource()))
                )
                .then(literalArgumentBuilder("refreshcommands")
                              .requires(source -> source.hasPermission("discord.admin"))
                              .executes(context -> recreateCommands(context.getSource()))
                )
                .then(createLinkNode())
                .then(createUnlinkNode())
                .build();

        BrigadierCommand topCommand = new BrigadierCommand(topNode);
        BrigadierCommand linkCommand = new BrigadierCommand(createLinkNode().build()); // Make /link alias for /discord link
        BrigadierCommand unlinkCommand = new BrigadierCommand(createUnlinkNode().build()); // Make /unlink alias for /discord unlink

        CommandManager commandManager = plugin.getProxy().getCommandManager();

        // Register commands
        CommandMeta topMeta = commandManager.metaBuilder("discord").plugin(plugin).build();
        CommandMeta linkMeta = commandManager.metaBuilder("link").plugin(plugin).build();
        CommandMeta unlinkMeta = commandManager.metaBuilder("unlink").plugin(plugin).build();

        commandManager.register(topMeta, topCommand);
        commandManager.register(linkMeta, linkCommand);
        commandManager.register(unlinkMeta, unlinkCommand);
    }

    private LiteralArgumentBuilder<CommandSource> createLinkNode() {
        return literalArgumentBuilder("link")
                .requires(source -> source.hasPermission("discord.link"))
                .then(
                        requiredArgumentBuilder("player", StringArgumentType.word())
                                .suggests(playerSuggestions)
                                .then(requiredArgumentBuilder("discordId", LongArgumentType.longArg(0))
                                              .executes(this::link))
                );
    }

    private LiteralArgumentBuilder<CommandSource> createUnlinkNode() {
        return literalArgumentBuilder("unlink")
                .requires(source -> source.hasPermission("discord.unlink"))
                .executes(this::unlink)
                .then(
                        requiredArgumentBuilder("player", StringArgumentType.word())
                                .requires(source -> source.hasPermission("discord.unlink.other"))
                                .suggests(playerSuggestions)
                                .executes(this::unlinkOther)
                );
    }

    public int link(CommandContext<CommandSource> ctx) {
        CommandSource sender = ctx.getSource();
        String target = ctx.getArgument("player", String.class);
        Long discordId = ctx.getArgument("discordId", Long.class);

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
                    plugin.getDiscord().getJDA().retrieveUserById(linkedDiscord)
                            .submit()
                            .thenAcceptAsync(user -> Messages.sendComponent(
                                    sender,
                                    "link-other-already-linked-known",
                                    Map.of("player", target, "discord", user.getName()),
                                    Collections.emptyMap()))
                            .exceptionally(_ -> {
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

                plugin.getDiscord().getJDA().retrieveUserById(discordId)
                        .submit()
                        .thenComposeAsync(result -> {
                            discordUser[0] = result;
                            return userManager.lookupUsername(linkedMinecraft);
                        })
                        .thenAcceptAsync(minecraftUsername -> {
                            String discordUsername = discordUser[0] != null ? discordUser[0].getName() : String.valueOf(
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
            plugin.getDiscord().getJDA().retrieveUserById(discordId).submit().thenAcceptAsync(user -> {
                LinkResult result = linkingManager.manualLink(uuid, user.getIdLong());

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
                            "discord", user.getName(),
                            "player", target), Collections.emptyMap());
                }
            }).exceptionally(error -> {
                sender.sendMessage(Component.text(error.toString()).color(NamedTextColor.RED));
                return null;
            });
        });

        return Command.SINGLE_SUCCESS;
    }

    public int unlink(CommandContext<CommandSource> ctx) {
        if (!(ctx.getSource() instanceof Player player)) {
            Messages.sendComponent(ctx.getSource(), "unlink-not-player");
            return Command.SINGLE_SUCCESS;
        }

        if(linkingManager.isLinked(player)) { //Unlinking self
            linkingManager.unlink(player);
        } else {
            Messages.sendComponent(player, "unlink-not-linked");
        }

        return Command.SINGLE_SUCCESS;
    }

    //Unlinking another player
    public int unlinkOther(CommandContext<CommandSource> ctx) {
        CommandSource sender = ctx.getSource();
        String target = ctx.getArgument("player", String.class);
        String actorName = sender instanceof Player player ? player.getUsername() : "Console";

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
                                           Collections.singletonMap("player", actorName),
                                           Collections.emptyMap());
                }
            } else {
                Messages.sendComponent(sender, "unlink-other-discord-not-linked",
                                                                         Collections.singletonMap("player", target),
                                                                         Collections.emptyMap());
            }

            return Command.SINGLE_SUCCESS;
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
                                           Collections.singletonMap("player", actorName),
                                           Collections.emptyMap());
                }
            } else {
                Messages.sendComponent(sender, "unlink-other-not-linked",
                                                         Collections.singletonMap("player", target),
                                                         Collections.emptyMap());
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private int save(CommandSource sender) {
        linkingManager.saveLinks();
        Messages.sendComponent(sender, "save-success");
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandSource sender) {
        plugin.reload();
        Messages.sendComponent(sender, "reload-success");
        return Command.SINGLE_SUCCESS;
    }

    private int recreateCommands(CommandSource sender) {
        plugin.getDiscord().createSlashCommands()
                .thenAccept(_ -> Messages.sendComponent(sender, "refresh-commands-success"))
                .exceptionally(e -> {
                    plugin.getLogger().error("Failed to refresh commands", e);
                    Messages.sendComponent(sender, "refresh-commands-error");
                    return null;
                });
        return Command.SINGLE_SUCCESS;
    }
}
