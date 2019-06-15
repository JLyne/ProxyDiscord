package me.prouser123.bungee.discord;

import me.lucko.luckperms.LuckPerms;
import me.lucko.luckperms.api.DataMutateResult;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Node;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VerificationManager {
    private static LinkingManager linkingManager = null;
    private static String verifiedPermission = null;
    private ArrayList<Long> verifiedRoleUsers;
    private static LuckPermsApi luckPermsApi = null;

    VerificationManager(Role verifiedRole, String verifiedPermission) {
        verifiedRoleUsers = new ArrayList<>();
        linkingManager = Main.inst().getLinkingManager();

        VerificationManager.verifiedPermission = verifiedPermission;
        VerificationManager.luckPermsApi = LuckPerms.getApi();

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

        if(linked != null) {
            ProxiedPlayer player = ProxyServer.getInstance().getPlayer(UUID.fromString(linked));

            if(player != null) {
                addVerifiedPermission(player);
            }
        }
    }

    public void removeUser(User user) {
        verifiedRoleUsers.remove(user.getId());

        String linked = linkingManager.getLinked(user);

        if(linked != null) {
            ProxiedPlayer player = ProxyServer.getInstance().getPlayer(UUID.fromString(linked));

            if(player != null) {
                removeVerifiedPermission(player);
            }
        }
    }

    public boolean hasVerifiedRole(User user) {
        return verifiedRoleUsers.contains(user.getId());
    }

    public boolean hasVerifiedRole(Long id) {
        return verifiedRoleUsers.contains(id);
    }

    public VerificationResult checkVerificationStatus(ProxiedPlayer player) {
        if(player.hasPermission(verifiedPermission)) {
            Main.inst().getDebugLogger().info("Player " + player.getName() + " already has verified permission. Epic.");
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
            return VerificationResult.LINKED_NOT_VERIFIED;
        }

        Main.inst().getDebugLogger().info("Player " + player.getName() + " hasn't linked discord. Very sad.");
        return VerificationResult.NOT_LINKED;
    }

    public VerificationResult checkVerificationStatus(Long discordId) {
        String linked = linkingManager.getLinked(discordId);

        if(linked == null) {
            Main.inst().getDebugLogger().info("Discord id " + discordId.toString() + " has not been linked to a player. Sad.");
            return VerificationResult.NOT_LINKED;
        }

        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(UUID.fromString(linked));

        if(hasVerifiedRole(discordId)) {
            if(player != null) {
                addVerifiedPermission(player);
            }

            return VerificationResult.VERIFIED;
        } else {
            if(player != null) {
                removeVerifiedPermission(player);
            }

            return VerificationResult.LINKED_NOT_VERIFIED;
        }
    }

    public VerificationResult checkVerificationStatus(User user) {
        return checkVerificationStatus(user.getId());
    }

    private void addVerifiedPermission(ProxiedPlayer player) {
        if(player.hasPermission(verifiedPermission)) {
            return;
        }

        me.lucko.luckperms.api.User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());
        Node node = luckPermsApi.getNodeFactory().newBuilder(verifiedPermission).build();
        user.setTransientPermission(node);
    }

    private void removeVerifiedPermission(ProxiedPlayer player) {
        me.lucko.luckperms.api.User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());
        Node node = luckPermsApi.getNodeFactory().newBuilder(verifiedPermission).build();
        user.unsetTransientPermission(node);
    }
}
