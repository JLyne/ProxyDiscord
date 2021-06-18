package uk.co.notnull.proxydiscord.manager;

import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import ninja.leaping.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.RemovalReason;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class LuckPermsManager {
    private final String verifiedPermission;

    private final ProxyDiscord plugin;
    private final Logger logger;
    private final UserManager userManager;

    public LuckPermsManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        LuckPerms luckPermsApi = LuckPermsProvider.get();
        userManager = luckPermsApi.getUserManager();

        verifiedPermission = config.getNode("verified-permission").getString();
	}

	public void addVerifiedPermission(Player player) {
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

    public void removeVerifiedPermission(Player player, RemovalReason reason) {
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

    public UserManager getUserManager() {
        return userManager;
    }
}
