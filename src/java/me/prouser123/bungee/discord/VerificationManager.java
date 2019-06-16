package me.prouser123.bungee.discord;

import me.lucko.luckperms.LuckPerms;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Node;
import me.prouser123.bungee.discord.bot.commands.RemovalReason;
import me.prouser123.bungee.discord.bot.commands.listeners.UserRoleAdd;
import me.prouser123.bungee.discord.bot.commands.listeners.UserRoleRemove;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ServerConnectEvent;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class VerificationManager {
    private static LinkingManager linkingManager = null;

    private static String verifiedPermission = null;
    private static String bypassPermission = null;
    private static ServerInfo unverifiedServer = null;
    public static Role verifiedRole = null;

    private ArrayList<Long> verifiedRoleUsers;
    private static LuckPermsApi luckPermsApi = null;

    VerificationManager(Role verifiedRole, String verifiedPermission, String bypassPermission, ServerInfo unverifiedServer) {
        verifiedRoleUsers = new ArrayList<>();
        linkingManager = Main.inst().getLinkingManager();

        VerificationManager.verifiedPermission = verifiedPermission;
        VerificationManager.bypassPermission = bypassPermission;
        VerificationManager.unverifiedServer = unverifiedServer;
        VerificationManager.luckPermsApi = LuckPerms.getApi();
        VerificationManager.verifiedRole = verifiedRole;

        Discord.api.addUserRoleAddListener(new UserRoleAdd(verifiedRole));
        Discord.api.addUserRoleRemoveListener(new UserRoleRemove(verifiedRole));

        Collection<User> users = verifiedRole.getUsers();

        for(User user: users) {
            verifiedRoleUsers.add(user.getId());
        }
    }

    public void addUser(User user) {
        if (!verifiedRoleUsers.contains(user.getId())) {
            verifiedRoleUsers.add(user.getId());
        }

        String linked = linkingManager.getLinked(user);
        Main.inst().getDebugLogger().info("Removing verified status of " + user.getDiscriminatedName());

        if(linked != null) {
            ProxiedPlayer player = ProxyServer.getInstance().getPlayer(UUID.fromString(linked));

            if(player != null) {
                Main.inst().getDebugLogger().info("Linked account is currently on the server. Adding permission");
                addVerifiedPermission(player);
            }
        }
    }

    public void removeUser(User user) {
        verifiedRoleUsers.remove(user.getId());

        String linked = linkingManager.getLinked(user);
        Main.inst().getDebugLogger().info("Removing verified status of " + user.getDiscriminatedName());

        if(linked != null) {
            ProxiedPlayer player = ProxyServer.getInstance().getPlayer(UUID.fromString(linked));

            if(player != null) {
                Main.inst().getDebugLogger().info("Linked account is currently on the server. Removing permission");
                removeVerifiedPermission(player, RemovalReason.VERIFIED_ROLE_LOST);
            }
        }
    }

    public boolean hasVerifiedRole(Long id) {
        return verifiedRoleUsers.contains(id);
    }

    public VerificationResult checkVerificationStatus(ProxiedPlayer player) {
        if(player.hasPermission(bypassPermission)) {
            Main.inst().getDebugLogger().info("Player " + player.getName() + " has bypass permission. Epic.");
            addVerifiedPermission(player);

            return VerificationResult.VERIFIED;
        }

        Long linkedId = linkingManager.getLinked(player);

        if (linkedId != null) {
            if(hasVerifiedRole(linkedId)) {
                Main.inst().getDebugLogger().info("Player " + player.getName() + " has linked discord and has verified role. Epic.");
                addVerifiedPermission(player);

                return VerificationResult.VERIFIED;
            }

            Main.inst().getDebugLogger().info("Player " + player.getName() + " has linked discord, but doesn't have the verified role. Sad.");
            removeVerifiedPermission(player, RemovalReason.VERIFIED_ROLE_LOST);

            return VerificationResult.LINKED_NOT_VERIFIED;
        }

        Main.inst().getDebugLogger().info("Player " + player.getName() + " hasn't linked discord. Very sad.");
        removeVerifiedPermission(player, RemovalReason.UNLINKED);

        return VerificationResult.NOT_LINKED;
    }

    public VerificationResult checkVerificationStatus(Long discordId) {
        String linked = linkingManager.getLinked(discordId);

        if(linked == null) {
            Main.inst().getDebugLogger().info("Discord id " + discordId.toString() + " has not been linked to a player. Sad.");
            return VerificationResult.NOT_LINKED;
        }

        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(UUID.fromString(linked));

        if(player != null) {
            if(player.hasPermission(bypassPermission)) {
                Main.inst().getDebugLogger().info("Player " + player.getName() + " has bypass permission. Epic.");
                addVerifiedPermission(player);

                return VerificationResult.VERIFIED;
            }
        }

        if(hasVerifiedRole(discordId)) {
            Main.inst().getDebugLogger().info("Discord id " + discordId.toString() + " is linked and has verified role. Epic.");

            if(player != null) {
                Main.inst().getDebugLogger().info("Linked account is currently on the server. Adding permission");
                addVerifiedPermission(player);
            }

            return VerificationResult.VERIFIED;
        } else {
            Main.inst().getDebugLogger().info("Discord id " + discordId.toString() + " is linked, but doesn't have the verified role. Sad.");

            if(player != null) {
                Main.inst().getDebugLogger().info("Linked account is currently on the server. Removing permission");
                removeVerifiedPermission(player, RemovalReason.VERIFIED_ROLE_LOST);
            }

            return VerificationResult.LINKED_NOT_VERIFIED;
        }
    }

    private void addVerifiedPermission(ProxiedPlayer player) {
        if(player.hasPermission(verifiedPermission)) {
            return;
        }

        Main.inst().getDebugLogger().info("Adding verified permission to " + player.getName());

        me.lucko.luckperms.api.User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());
        Node node = luckPermsApi.getNodeFactory().newBuilder(verifiedPermission).build();
        user.setTransientPermission(node);
    }

    private void removeVerifiedPermission(ProxiedPlayer player, RemovalReason reason) {
        if(!player.hasPermission(verifiedPermission)) {
            return;
        }

        Main.inst().getDebugLogger().info("Removing verified permission from " + player.getName());

        me.lucko.luckperms.api.User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());

        if(user == null) {
            return;
        }

        Node node = luckPermsApi.getNodeFactory().newBuilder(verifiedPermission).build();
        user.unsetTransientPermission(node);

        TextComponent message;

        switch(reason) {
            case VERIFIED_ROLE_LOST:
                message = new TextComponent(ChatMessages.getMessage("verification-lost-role"));
                break;

            case UNLINKED:
                message = new TextComponent(ChatMessages.getMessage("verification-lost-unlinked"));
                break;
            default:
                message = new TextComponent("Something unexpected happened.");
        }

        message.setColor(ChatColor.RED);

        player.sendMessage(message);

        if(unverifiedServer == null) {
            Main.inst().getDebugLogger().info("No unverified server defined. Kicking " + player.getName());
            player.disconnect(message);

            return;
        }

        Server currentServer = player.getServer();

        if(currentServer != null && !currentServer.getInfo().equals(unverifiedServer)) {
            Main.inst().getDebugLogger().info("Moving " + player.getName() + " to " + unverifiedServer.getName());

            player.connect(unverifiedServer, (result, error) -> {
                if(!result) {
                    String text = ChatMessages.getMessage("verification-lost-moved");
                    TextComponent extra = new TextComponent(text.replace("[server]", unverifiedServer.getName()));
                    message.addExtra(extra);

                    player.sendMessage(message);
                } else {
                    Main.inst().getDebugLogger().info("Failed to move " + player.getName() + " to " + unverifiedServer.getName() + ". Kicking.");
                    player.disconnect(message);
                }
            }, ServerConnectEvent.Reason.PLUGIN);

            player.sendMessage(message);
        } else {
            player.sendMessage(message);
        }
    }

    public boolean canJoinServer(ServerInfo server, ProxiedPlayer player) {
        if(server.equals(unverifiedServer)) {
            return true;
        }

        return checkVerificationStatus(player) == VerificationResult.VERIFIED;
    }
}
