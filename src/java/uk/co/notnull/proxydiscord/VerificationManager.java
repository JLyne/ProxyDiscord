package uk.co.notnull.proxydiscord;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import ninja.leaping.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.events.PlayerVerifyStateChangeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VerificationManager {
    private final LinkingManager linkingManager;

    private final String verifiedPermission;
    private final String bypassPermission;
    private final String verifiedRoleId;
    private final RegisteredServer unverifiedServer;
    private final RegisteredServer defaultVerifiedServer;

    private final Set<Long> verifiedRoleUsers;

    private final ProxyServer proxy;
    private final Logger logger;
    private final ConcurrentHashMap<Player, VerificationResult> lastKnownStatuses;

    public VerificationManager(ConfigurationNode config) {
        this.proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();

        verifiedRoleUsers = ConcurrentHashMap.newKeySet();
        linkingManager = ProxyDiscord.inst().getLinkingManager();
        lastKnownStatuses = new ConcurrentHashMap<>();

        verifiedRoleId = config.getNode("verify-role-id").getString();
        verifiedPermission = config.getNode("verified-permission").getString();
        bypassPermission = config.getNode("bypass-permission").getString();

        String unverifiedServerName = config.getNode("unverified-server").getString();
        unverifiedServer = proxy.getServer(unverifiedServerName).orElse(null);

        if(unverifiedServer == null && unverifiedServerName != null && !unverifiedServerName.isEmpty()) {
            logger.warn("Unverified server (" + unverifiedServerName + ") does not exist!");
        }

        String defaultVerifiedServerName = config.getNode("default-verified-server").getString();
        defaultVerifiedServer = proxy.getServer(defaultVerifiedServerName).orElse(null);

        if(defaultVerifiedServer == null && defaultVerifiedServerName != null && !defaultVerifiedServerName.isEmpty()) {
            logger.warn("Default verified server (" + defaultVerifiedServerName + ") does not exist!");
        }

        if(verifiedRoleId != null) {
            populateUsers();
        }

        ProxyDiscord.inst().getDiscord().getApi().addReconnectListener(event -> {
            if(verifiedRoleId != null) {
                populateUsers();
            }
        });

        proxy.getEventManager().register(ProxyDiscord.inst(), this);
    }

    public void addUser(User user) {
        ProxyDiscord.inst().getDebugLogger().info("Adding verified status of " + user.getDiscriminatedName());

        verifiedRoleUsers.add(user.getId());
        checkVerificationStatus(user.getId());
    }

    public void removeUser(User user) {
        ProxyDiscord.inst().getDebugLogger().info("Removing verified status of " + user.getDiscriminatedName());

        verifiedRoleUsers.remove(user.getId());
        checkVerificationStatus(user.getId());
    }

    public boolean hasVerifiedRole(Long id) {
        return verifiedRoleUsers.contains(id);
    }

    public VerificationResult checkVerificationStatus(Player player) {
        VerificationResult status;

        if(verifiedRoleId == null) {
            ProxyDiscord.inst().getDebugLogger().info("No verified role defined. Considering " + player.getUsername() + " verified.");
            addVerifiedPermission(player);

            status = VerificationResult.VERIFIED;
        } else if(player.hasPermission(bypassPermission)) {
            ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " has bypass permission. Epic.");
            addVerifiedPermission(player);

            status = VerificationResult.VERIFIED;
        } else {
            Long linkedId = linkingManager.getLinked(player);

            if (linkedId != null) {
                if(hasVerifiedRole(linkedId)) {
                    ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " has linked discord and has verified role. Epic.");
                    addVerifiedPermission(player);

                    status = VerificationResult.VERIFIED;
                } else {
                    ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " has linked discord, but doesn't have the verified role. Sad.");
                    removeVerifiedPermission(player, RemovalReason.VERIFIED_ROLE_LOST);

                    status = VerificationResult.LINKED_NOT_VERIFIED;
                }
            } else {
                ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " hasn't linked discord. Very sad.");
                removeVerifiedPermission(player, RemovalReason.UNLINKED);

                status = VerificationResult.NOT_LINKED;
            }
        }

        VerificationResult finalStatus = status;
        lastKnownStatuses.compute(player, (key, value) -> {
            ProxyDiscord.inst().getDebugLogger().info("Updating status cache for " + player.getUsername());

            if(value != null && value != finalStatus) {
                ProxyDiscord.inst().getDebugLogger().info("PlayerVerifyStateChangeEvent " + finalStatus + " from " + value);
                proxy.getEventManager().fireAndForget(new PlayerVerifyStateChangeEvent(player, finalStatus, value));
            }

            return finalStatus;
        });

        return status;
    }

    VerificationResult checkVerificationStatus(Long discordId) {
        VerificationResult status;

        UUID linked = linkingManager.getLinked(discordId);
        Optional<Player> player = Optional.empty();

        if(verifiedRoleId == null) {
            ProxyDiscord.inst().getDebugLogger().info("No verified role defined. Considering " + discordId.toString() + " verified.");

            status = VerificationResult.VERIFIED;
        } else if(linked == null) {
            ProxyDiscord.inst().getDebugLogger().info("Discord id " + discordId.toString() + " has not been linked to a player. Sad.");
            status = VerificationResult.NOT_LINKED;
        } else {
            player = proxy.getPlayer(linked);

            if(player.isPresent() && player.get().hasPermission(bypassPermission)) {
                ProxyDiscord.inst().getDebugLogger().info("Player " + player.get().getUsername() + " has bypass permission. Epic.");
                addVerifiedPermission(player.get());

                status = VerificationResult.VERIFIED;
            } else if(hasVerifiedRole(discordId)) {
                ProxyDiscord.inst().getDebugLogger().info("Discord id " + discordId.toString() + " is linked and has verified role. Epic.");

                if(player.isPresent()) {
                    ProxyDiscord.inst().getDebugLogger().info("Linked account is currently on the server. Adding permission");
                    addVerifiedPermission(player.get());
                }

                status = VerificationResult.VERIFIED;
            } else {
                ProxyDiscord.inst().getDebugLogger().info("Discord id " + discordId.toString() + " is linked, but doesn't have the verified role. Sad.");

                if(player.isPresent()) {
                    ProxyDiscord.inst().getDebugLogger().info("Linked account is currently on the server. Removing permission");
                    removeVerifiedPermission(player.get(), RemovalReason.VERIFIED_ROLE_LOST);
                }

                status = VerificationResult.LINKED_NOT_VERIFIED;
            }
        }

        VerificationResult finalStatus = status;

        player.ifPresent(p -> lastKnownStatuses.compute(p, (key, value) -> {
            ProxyDiscord.inst().getDebugLogger().info("Updating status cache for " + p.getUsername());

            if(value != null && value != finalStatus) {
                ProxyDiscord.inst().getDebugLogger().info("PlayerVerifyStateChangeEvent " + finalStatus + " from " + value);
                proxy.getEventManager().fireAndForget(new PlayerVerifyStateChangeEvent(p, finalStatus, value));
            }

            return finalStatus;
        }));

        return status;
    }

    private void addVerifiedPermission(Player player) {
        if(player.hasPermission(verifiedPermission)) {
            return;
        }

        ProxyDiscord.inst().getDebugLogger().info("Adding verified permission to " + player.getUsername());

        try {
            LuckPerms luckPermsApi = LuckPermsProvider.get();
            net.luckperms.api.model.user.@Nullable User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());

            if(user == null) {
                return;
            }

            Node node = Node.builder(verifiedPermission).build();
            user.transientData().add(node);
        } catch(IllegalStateException e) {
             logger.warn("Failed to update permissions: " + e.getMessage());
        }
    }

    private void removeVerifiedPermission(Player player, RemovalReason reason) {
        if(!player.hasPermission(verifiedPermission)) {
            return;
        }

        ProxyDiscord.inst().getDebugLogger().info("Removing verified permission from " + player.getUsername());

        try {
            LuckPerms luckPermsApi = LuckPermsProvider.get();
            net.luckperms.api.model.user.@Nullable User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());

            if(user == null) {
                return;
            }

            Node node = Node.builder(verifiedPermission).build();
            user.transientData().remove(node);
        } catch (IllegalStateException e) {
            logger.warn("Failed to update permissions: " + e.getMessage());
        }
    }

    public void populateUsers() {
        Optional<Role> verifiedRole = ProxyDiscord.inst().getDiscord().getApi().getRoleById(verifiedRoleId);

        if(!verifiedRole.isPresent()) {
            if(verifiedRoleId != null && !verifiedRoleId.isEmpty()) {
                logger.warn("Failed to load verified role (" + verifiedRoleId + "). Is the ID correct or is discord down?)");
            }

            return;
        }

        logger.info("Role verification enabled for role " + verifiedRole.get().getName());

        Collection<User> users = verifiedRole.get().getUsers();

        verifiedRoleUsers.clear();

        for(User user: users) {
            verifiedRoleUsers.add(user.getId());
        }

        proxy.getAllPlayers().forEach(this::checkVerificationStatus);
    }

    public void clearPlayerStatus(Player player) {
		lastKnownStatuses.remove(player);
	}

    public String getVerifiedRoleId() {
        return verifiedRoleId;
    }

    Role getVerifiedRole() {
        Optional<Role> role = ProxyDiscord.inst().getDiscord().getApi().getRoleById(verifiedRoleId);

        return role.orElse(null);
    }

    public RegisteredServer getUnverifiedServer() {
        return unverifiedServer;
    }

    public RegisteredServer getDefaultVerifiedServer() {
        return defaultVerifiedServer;
    }
}
