package uk.co.notnull.proxydiscord;

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
import java.util.stream.Collectors;

public class VerificationManager {
    private final LinkingManager linkingManager;

    private final Set<RegisteredServer> unverifiedServers;
    private RegisteredServer defaultVerifiedServer;
    private RegisteredServer linkingServer;

    private String verifiedPermission;
    private String bypassPermission;
    private Set<String> verifiedRoleIds;

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
        unverifiedServers = new HashSet<>();

        proxy.getEventManager().register(ProxyDiscord.inst(), this);
        parseConfig(config);
    }

    public void parseConfig(ConfigurationNode config) {
        ConfigurationNode roleIds = config.getNode("verified-role-ids");
        verifiedRoleIds = new HashSet<>();

        if(!roleIds.isEmpty()) {
            if(roleIds.isList()) {

                List<? extends ConfigurationNode> children = roleIds.getChildrenList();

                children.forEach((ConfigurationNode child) -> {
                    if(!child.isEmpty() && !child.isMap() && !child.isList()) {
                        verifiedRoleIds.add(child.getString());
                    }
                });

            } else {
                verifiedRoleIds = Collections.singleton(roleIds.getString());
            }
        }

        verifiedPermission = config.getNode("verified-permission").getString();
        bypassPermission = config.getNode("bypass-permission").getString();
        String linkingServerName = config.getNode("linking-server").getString();
        linkingServer = proxy.getServer(linkingServerName).orElse(null);

        unverifiedServers.clear();

        if(linkingServer == null && linkingServerName != null && !linkingServerName.isEmpty()) {
            logger.warn("Linking server (" + linkingServerName + ") does not exist!");
        } else if(linkingServer != null) {
            unverifiedServers.add(linkingServer);
        }

        List<? extends ConfigurationNode> unverifiedServerNames = config.getNode("unverified-servers").getChildrenList();

        for(ConfigurationNode unverifiedServerName : unverifiedServerNames) {
            String name = unverifiedServerName.getString();
            Optional<RegisteredServer> server = proxy.getServer(name);

            server.ifPresent(unverifiedServers::add);

            if(server.isEmpty() && name != null && !name.isEmpty()) {
                logger.warn("Unverified server (" + name + ") does not exist!");
            }
        }

        String defaultVerifiedServerName = config.getNode("default-verified-server").getString();
        defaultVerifiedServer = proxy.getServer(defaultVerifiedServerName).orElse(null);

        if(defaultVerifiedServer == null && defaultVerifiedServerName != null && !defaultVerifiedServerName.isEmpty()) {
            logger.warn("Default verified server (" + defaultVerifiedServerName + ") does not exist!");
        }

        if(!verifiedRoleIds.isEmpty()) {
            populateUsers();
        }
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

        if(verifiedRoleIds.isEmpty()) {
            ProxyDiscord.inst().getDebugLogger().info("No verified role defined. Considering " + player.getUsername() + " verified.");
            addVerifiedPermission(player);

            status = VerificationResult.VERIFIED;
        } else if(player.hasPermission(bypassPermission)) {
            ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " has bypass permission.");
            addVerifiedPermission(player);

            status = VerificationResult.VERIFIED;
        } else {
            Long linkedId = linkingManager.getLinked(player);

            if (linkedId != null) {
                if(hasVerifiedRole(linkedId)) {
                    ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " has linked discord and has verified role.");
                    addVerifiedPermission(player);

                    status = VerificationResult.VERIFIED;
                } else {
                    ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " has linked discord, but doesn't have the verified role.");
                    removeVerifiedPermission(player, RemovalReason.VERIFIED_ROLE_LOST);

                    status = VerificationResult.LINKED_NOT_VERIFIED;
                }
            } else {
                ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " hasn't linked discord.");
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

        if(verifiedRoleIds.isEmpty()) {
            ProxyDiscord.inst().getDebugLogger().info("No verified role defined. Considering " + discordId.toString() + " verified.");

            status = VerificationResult.VERIFIED;
        } else if(linked == null) {
            ProxyDiscord.inst().getDebugLogger().info("Discord id " + discordId.toString() + " has not been linked to a player.");
            status = VerificationResult.NOT_LINKED;
        } else {
            player = proxy.getPlayer(linked);

            if(player.isPresent() && player.get().hasPermission(bypassPermission)) {
                ProxyDiscord.inst().getDebugLogger().info("Player " + player.get().getUsername() + " has bypass permission.");
                addVerifiedPermission(player.get());

                status = VerificationResult.VERIFIED;
            } else if(hasVerifiedRole(discordId)) {
                ProxyDiscord.inst().getDebugLogger().info("Discord id " + discordId.toString() + " is linked and has verified role.");

                if(player.isPresent()) {
                    ProxyDiscord.inst().getDebugLogger().info("Linked account is currently on the server. Adding permission");
                    addVerifiedPermission(player.get());
                }

                status = VerificationResult.VERIFIED;
            } else {
                ProxyDiscord.inst().getDebugLogger().info("Discord id " + discordId.toString() + " is linked, but doesn't have the verified role.");

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
        verifiedRoleUsers.clear();

        verifiedRoleIds.forEach((String roleId) -> {
            Optional<Role> verifiedRole = ProxyDiscord.inst().getDiscord().getApi().getRoleById(roleId);

            if(verifiedRole.isEmpty()) {
                logger.warn("Failed to load verified role (" + roleId + "). Is the ID incorrect or is discord down?)");

                return;
            }

            logger.info("Role verification enabled for role " + verifiedRole.get().getName());

            Collection<User> users = verifiedRole.get().getUsers();

            for(User user: users) {
                verifiedRoleUsers.add(user.getId());
            }
        });

        proxy.getAllPlayers().forEach(this::checkVerificationStatus);
    }

    public void clearPlayerStatus(Player player) {
		lastKnownStatuses.remove(player);
	}

    public Set<String> getVerifiedRoleIds() {
        return verifiedRoleIds;
    }

    public Set<Role> getVerifiedRoles() {
        return verifiedRoleIds.stream().map((String roleId) -> {
            Optional<Role> role = ProxyDiscord.inst().getDiscord().getApi().getRoleById(roleId);

            return role.orElse(null);
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public Set<RegisteredServer> getUnverifiedServers() {
        return unverifiedServers;
    }

    public boolean isUnverifiedServer(RegisteredServer server) {
        return unverifiedServers.contains(server);
    }

    public RegisteredServer getDefaultVerifiedServer() {
        return defaultVerifiedServer;
    }

    public RegisteredServer getLinkingServer() {
        return linkingServer;
    }

    public boolean isLinkingServer(RegisteredServer server) {
        return server.equals(linkingServer);
    }
}
