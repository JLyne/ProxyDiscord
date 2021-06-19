package uk.co.notnull.proxydiscord.manager;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.Flag;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import ninja.leaping.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.events.PlayerVerifyStateChangeEvent;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class LuckPermsManager {
    private final String verifiedPermission;

    private final ProxyDiscord plugin;
    private final Logger logger;
    private final UserManager userManager;
    private final QueryOptions groupQuery;

    public LuckPermsManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        LuckPerms luckPermsApi = LuckPermsProvider.get();
        groupQuery = QueryOptions.nonContextual(
                Set.of(Flag.INCLUDE_NODES_WITHOUT_SERVER_CONTEXT, Flag.INCLUDE_NODES_WITHOUT_WORLD_CONTEXT));
        userManager = luckPermsApi.getUserManager();

        verifiedPermission = config.getNode("verified-permission").getString();

        plugin.getProxy().getEventManager().register(plugin, this);
	}

	@Subscribe(order = PostOrder.NORMAL)
    public void onPlayerVerifyStateChange(PlayerVerifyStateChangeEvent event) {
        Player player = event.getPlayer();

        if(event.getState().isVerified()) {
            addVerifiedPermission(player);
        } else {
            removeVerifiedPermission(player);
        }
    }

	private void addVerifiedPermission(Player player) {
        if(player.hasPermission(verifiedPermission)) {
            return;
        }

        plugin.getDebugLogger().info("Adding verified permission to " + player.getUsername());

        try {

            net.luckperms.api.model.user.@Nullable User user = userManager.getUser(player.getUniqueId());

            if(user == null) {
                return;
            }

            Node node = Node.builder(verifiedPermission).build();
            user.transientData().add(node);
        } catch(IllegalStateException e) {
            logger.warn("Failed to update permissions: " + e.getMessage());
        }
    }

    private void removeVerifiedPermission(Player player) {
        if(!player.hasPermission(verifiedPermission)) {
            return;
        }

        plugin.getDebugLogger().info("Removing verified permission from " + player.getUsername());

        try {
            net.luckperms.api.model.user.@Nullable User user = userManager.getUser(player.getUniqueId());

            if(user == null) {
                return;
            }

            Node node = Node.builder(verifiedPermission).build();
            user.transientData().remove(node);
        } catch (IllegalStateException e) {
            logger.warn("Failed to update permissions: " + e.getMessage());
        }
    }

    /**
     * Update the groups for a user based on the supplied groups to add and check
     * @param player - Player to update groups for
     * @param groupsToAdd - Groups to add to the player, if they don't already have them
     * @param groupsToCheck - Groups to check for and remove from the player if they have them and they aren't in groupsToAdd
     * @return CompletableFuture containing a boolean. True if the user's groups were updated, false if there were no changes
     */
    public @NonNull CompletableFuture<Boolean> updateUserGroups(Player player, Set<String> groupsToAdd, Set<String> groupsToCheck) {
        User user = userManager.getUser(player.getUniqueId());

        if(user == null) {
            return CompletableFuture.completedFuture(false);
        }

        AtomicBoolean changed = new AtomicBoolean(false);
        CachedPermissionData permissionData = user.getCachedData().getPermissionData(groupQuery);

        groupsToCheck.forEach((String group) -> {
            // Add groupsToAdd groups if the user doesn't have them explicitly set
            if(groupsToAdd.contains(group)) {
                plugin.getDebugLogger().info(group + " Not true? " + permissionData.checkPermission("group." + group));
                if(permissionData.checkPermission("group." + group) != Tristate.TRUE) {
                    user.data().add(Node.builder("group." + group).build());
                    changed.set(true);
                }
            } else {
                //Remove groupsToCheck groups if the user has them explicity set, and the group isn't in groupsToAdd
                plugin.getDebugLogger().info(group + " Not undefined? " + permissionData.checkPermission("group." + group));
                if(permissionData.checkPermission("group." + group) != Tristate.UNDEFINED) {
                    user.data().remove(Node.builder("group." + group).build());
                    changed.set(true);
                }
            }
        });

        // Only save if any changes occurred
        if(changed.get()) {
            plugin.getDebugLogger().info("Saving changes");
            return userManager.saveUser(user).thenApply((ignored) -> true);
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    public UserManager getUserManager() {
        return userManager;
    }
}
