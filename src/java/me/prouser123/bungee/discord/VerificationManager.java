package me.prouser123.bungee.discord;

import me.glaremasters.deluxequeues.DeluxeQueues;
import me.lucko.luckperms.LuckPerms;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Node;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.config.Configuration;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class VerificationManager {
    private final LinkingManager linkingManager;
    private final KickManager kickManager;

    private final String verifiedPermission;
    private final String bypassPermission;
    private final String verifiedRoleId;
    private final ServerInfo unverifiedServer;

    private final ArrayList<Long> verifiedRoleUsers;
    private final LuckPermsApi luckPermsApi;

    VerificationManager(Configuration config) {
        luckPermsApi = LuckPerms.getApi();

        verifiedRoleUsers = new ArrayList<>();
        linkingManager = Main.inst().getLinkingManager();
        kickManager = Main.inst().getKickManager();

        verifiedRoleId = config.getString("verify-role-id");
        verifiedPermission = config.getString("verified-permission");
        bypassPermission = config.getString("bypass-permission");

        String unverifiedServerName = config.getString("unverified-server");
        unverifiedServer = Main.inst().getProxy().getServerInfo(unverifiedServerName);

        if(unverifiedServer == null && unverifiedServerName != null && !unverifiedServerName.isEmpty()) {
            Main.inst().getLogger().warning("Unverified server (" + unverifiedServerName + ") does not exist!");
        }

        if(verifiedRoleId != null) {
            populateUsers();
        }

        Main.inst().getDiscord().getApi().addReconnectListener(event -> {
            if(verifiedRoleId != null) {
                populateUsers();
            }
        });
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
        if(verifiedRoleId == null) {
            Main.inst().getDebugLogger().info("No verified role defined. Considering " + player.getName() + " verified.");
            return VerificationResult.VERIFIED;
        }

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

    VerificationResult checkVerificationStatus(Long discordId) {
        if(verifiedRoleId == null) {
            Main.inst().getDebugLogger().info("No verified role defined. Considering " + discordId.toString() + " verified.");
            return VerificationResult.VERIFIED;
        }

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
        kickManager.removePlayer(player);
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

        if(unverifiedServer == null) {
            Main.inst().getDebugLogger().info("No unverified server defined. Kicking " + player.getName());
            player.disconnect(message);

            return;
        }

        //Remove player from any queues
        DeluxeQueues deluxeQueues = (DeluxeQueues) Main.inst().getProxy().getPluginManager().getPlugin("DeluxeQueues");

        if(deluxeQueues != null) {
            deluxeQueues.getQueueHandler().clearPlayer(player);
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
        } else if(reason != RemovalReason.UNLINKED) {
            player.sendMessage(message);
        }

        kickManager.addPlayer(player);
    }

    public void populateUsers() {
        Optional<Role> verifiedRole = Main.inst().getDiscord().getApi().getRoleById(verifiedRoleId);

        if(!verifiedRole.isPresent()) {
            if(verifiedRoleId != null && !verifiedRoleId.isEmpty()) {
                Main.inst().getLogger().warning("Failed to load verified role (" + verifiedRoleId + "). Is the ID correct or is discord down?)");
            }

            return;
        }

        Main.inst().getLogger().info("Role verification enabled for role " + verifiedRole.get().getName());

        Collection<User> users = verifiedRole.get().getUsers();

        for(User user: users) {
            verifiedRoleUsers.add(user.getId());
        }
    }

    public String getVerifiedRoleId() {
        return verifiedRoleId;
    }

    Role getVerifiedRole() {
        Optional<Role> role = Main.inst().getDiscord().getApi().getRoleById(verifiedRoleId);

        return role.orElse(null);
    }

    public ServerInfo getUnverifiedServer() {
        return unverifiedServer;
    }
}
