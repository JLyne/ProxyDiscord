package uk.co.notnull.proxydiscord;

import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import ninja.leaping.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

public class LuckPermsManager {
    private final String verifiedPermission;

    private final Logger logger;
    private final UserManager userManager;

    public LuckPermsManager(ConfigurationNode config) {
        this.logger = ProxyDiscord.inst().getLogger();

        LuckPerms luckPermsApi = LuckPermsProvider.get();
        userManager = luckPermsApi.getUserManager();

        verifiedPermission = config.getNode("verified-permission").getString();
	}

	public void addVerifiedPermission(Player player) {
        if(player.hasPermission(verifiedPermission)) {
            return;
        }

        ProxyDiscord.inst().getDebugLogger().info("Adding verified permission to " + player.getUsername());

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

        ProxyDiscord.inst().getDebugLogger().info("Removing verified permission from " + player.getUsername());

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
