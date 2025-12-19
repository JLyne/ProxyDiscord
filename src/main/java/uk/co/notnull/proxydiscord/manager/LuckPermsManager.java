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

package uk.co.notnull.proxydiscord.manager;

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
import org.spongepowered.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.api.events.PlayerVerifyStateChangeEvent;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class LuckPermsManager {
    private String verifiedPermission;

    private final ProxyDiscord plugin;
    private final Logger logger;
    private LuckPerms luckperms;
    private UserManager userManager;
    private QueryOptions groupQuery;

    public LuckPermsManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        parseConfig(config);
	}

	public void init() {
        //Luckperms isn't loaded until ProxyInitializeEvent
        luckperms = LuckPermsProvider.get();
        userManager = luckperms.getUserManager();
        groupQuery = QueryOptions.nonContextual(
                Set.of(Flag.INCLUDE_NODES_WITHOUT_SERVER_CONTEXT, Flag.INCLUDE_NODES_WITHOUT_WORLD_CONTEXT));
    }

	private void parseConfig(ConfigurationNode config) {
        verifiedPermission = config.node("linking", "verified-permission").getString();
    }

	@Subscribe()
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
			logger.warn("Failed to update permissions: {}", e.getMessage());
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
			logger.warn("Failed to update permissions: {}", e.getMessage());
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

        plugin.getDebugLogger().info(String.format("Updating groups for %s. Groups to add: %s, groups to check: %s",
												   player.getUsername(), groupsToAdd, groupsToCheck));

        groupsToCheck.forEach((String group) -> {
            // Add groupsToAdd groups if the user doesn't have them explicitly set
            if(groupsToAdd.contains(group)) {
                if(permissionData.checkPermission("group." + group) != Tristate.TRUE) {
                    user.data().add(Node.builder("group." + group).build());
                    changed.set(true);
                }
            } else {
                //Remove groupsToCheck groups if the user has them explicity set, and the group isn't in groupsToAdd
                if(permissionData.checkPermission("group." + group) != Tristate.UNDEFINED) {
                    user.data().remove(Node.builder("group." + group).build());
                    changed.set(true);
                }
            }
        });

        // Only save if any changes occurred
        if(changed.get()) {
            return userManager.saveUser(user)
                    .thenApply((ignored) -> {
                        luckperms.getMessagingService()
                                .ifPresent((service) -> service.pushUserUpdate(user));
                        return true;
                    }).exceptionally(e -> {
						logger.warn("Failed to save and propagate groups for {}", user.getUsername(), e);
                        return false;
                    });
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    public UserManager getUserManager() {
        if(userManager == null) {
            throw new IllegalStateException("Calling getUserManager too early. Luckperms hasn't loaded yet");
        }

        return userManager;
    }

    public void reload(ConfigurationNode config) {
        parseConfig(config);
    }
}
