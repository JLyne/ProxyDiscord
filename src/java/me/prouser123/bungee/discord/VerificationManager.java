package me.prouser123.bungee.discord;

import me.lucko.luckperms.LuckPerms;
import me.lucko.luckperms.api.DataMutateResult;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Node;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;

import java.util.ArrayList;
import java.util.Collection;

public class VerificationManager {
    private static LinkingManager linkingManager = null;
    private static String verifiedGroup = null;
    private ArrayList<Long> verifiedRoleUsers;
    private static LuckPermsApi luckPermsApi = null;

    VerificationManager(Role verifiedRole, String verifiedGroup) {
        verifiedRoleUsers = new ArrayList<>();
        linkingManager = Main.inst().getLinkingManager();

        VerificationManager.verifiedGroup = verifiedGroup;
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
    }

    public void removeUser(User user) {
        verifiedRoleUsers.remove(user.getId());
    }

    public boolean hasVerifiedRole(User user) {
        return verifiedRoleUsers.contains(user.getId());
    }

    public boolean hasVerifiedRole(Long id) {
        return verifiedRoleUsers.contains(id);
    }

    public VerificationResult checkVerificationStatus(ProxiedPlayer player) {
        if(player.hasPermission(verifiedGroup)) {
            Main.inst().getDebugLogger().info("Player " + player.getName() + " already has verified permission. Epic.");
            return VerificationResult.VERIFIED;
        }

        Long linkedId = linkingManager.getLinked(player);

        if (linkedId != null) {
            if(hasVerifiedRole(linkedId)) {
                Main.inst().getDebugLogger().info("Player " + player.getName() + " has linked discord and has verified role. Giving them verified permission. Epic.");

                me.lucko.luckperms.api.User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());
                Node node = luckPermsApi.getNodeFactory().makeGroupNode(verifiedGroup).setExpiry(3600).build();
                DataMutateResult result = user.setPermission(node);
                Main.inst().getDebugLogger().info("Adding permission to " + player.getName() + ". Result: " + result.toString());
                luckPermsApi.getUserManager().saveUser(user);

                return VerificationResult.VERIFIED;
            }

            Main.inst().getDebugLogger().info("Player " + player.getName() + " has linked discord, but doesn't have the verified role. Sad.");
            return VerificationResult.LINKED_NOT_VERIFIED;
        }

        Main.inst().getDebugLogger().info("Player " + player.getName() + " hasn't linked discord. Very sad.");
        return VerificationResult.NOT_LINKED;
    }
}
