package uk.co.notnull.proxydiscord.manager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import ninja.leaping.configurate.ConfigurationNode;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.RemovalReason;
import uk.co.notnull.proxydiscord.VerificationResult;
import uk.co.notnull.proxydiscord.events.PlayerVerifyStateChangeEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VerificationManager {
    private final LinkingManager linkingManager;
    private final LuckPermsManager luckpermsManager;

    private final Set<RegisteredServer> publicServers;
    private RegisteredServer defaultVerifiedServer;
    private RegisteredServer linkingServer;

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
        luckpermsManager = ProxyDiscord.inst().getLuckpermsManager();
        lastKnownStatuses = new ConcurrentHashMap<>();
        publicServers = new HashSet<>();

        proxy.getEventManager().register(ProxyDiscord.inst(), this);
        parseConfig(config);
    }

    private void parseConfig(ConfigurationNode config) {
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

        bypassPermission = config.getNode("bypass-permission").getString();
        String linkingServerName = config.getNode("linking-server").getString();
        linkingServer = proxy.getServer(linkingServerName).orElse(null);

        publicServers.clear();

        if(linkingServer == null && linkingServerName != null && !linkingServerName.isEmpty()) {
            logger.warn("Linking server (" + linkingServerName + ") does not exist!");
        } else if(linkingServer != null) {
            publicServers.add(linkingServer);
        }

        List<? extends ConfigurationNode> publicServerNames = config.getNode("public-servers").getChildrenList();

        for(ConfigurationNode serverName : publicServerNames) {
            String name = serverName.getString();
            Optional<RegisteredServer> server = proxy.getServer(name);

            server.ifPresent(publicServers::add);

            if(server.isEmpty() && name != null && !name.isEmpty()) {
                logger.warn("Public server (" + name + ") does not exist!");
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

    private boolean hasVerifiedRole(Long id) {
        return verifiedRoleUsers.contains(id);
    }

    public VerificationResult checkVerificationStatus(Player player) {
        VerificationResult status;

        if(verifiedRoleIds.isEmpty()) {
            ProxyDiscord.inst().getDebugLogger().info("No verified roles defined. Considering " + player.getUsername() + " verified.");
            luckpermsManager.addVerifiedPermission(player);

            status = VerificationResult.VERIFIED;
        } else if(player.hasPermission(bypassPermission)) {
            ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " has bypass permission.");
            luckpermsManager.addVerifiedPermission(player);

            status = VerificationResult.VERIFIED;
        } else {
            Long linkedId = linkingManager.getLinked(player);

            if (linkedId != null) {
                if(hasVerifiedRole(linkedId)) {
                    luckpermsManager.addVerifiedPermission(player);

                    status = VerificationResult.VERIFIED;
                } else {
                    luckpermsManager.removeVerifiedPermission(player, RemovalReason.VERIFIED_ROLE_LOST);

                    status = VerificationResult.LINKED_NOT_VERIFIED;
                }
            } else {
                luckpermsManager.removeVerifiedPermission(player, RemovalReason.UNLINKED);

                status = VerificationResult.NOT_LINKED;
            }
        }

        ProxyDiscord.inst().getDebugLogger().info("Status for player " + player.getUsername() + ": " + status);

        updatePlayerStatus(player, status);

        return status;
    }

    public VerificationResult checkVerificationStatus(Long discordId) {
        VerificationResult status;

        UUID linked = linkingManager.getLinked(discordId);
        Optional<Player> player = Optional.empty();

        if(verifiedRoleIds.isEmpty()) {
            ProxyDiscord.inst().getDebugLogger().info("No verified roles defined. Considering " + discordId.toString() + " verified.");

            status = VerificationResult.VERIFIED;
        } else if(linked == null) {
            status = VerificationResult.NOT_LINKED;
        } else {
            player = proxy.getPlayer(linked);

            if(player.isPresent() && player.get().hasPermission(bypassPermission)) {
                ProxyDiscord.inst().getDebugLogger().info("Player " + player.get().getUsername() + " has bypass permission.");
                luckpermsManager.addVerifiedPermission(player.get());

                status = VerificationResult.VERIFIED;
            } else if(hasVerifiedRole(discordId)) {

                if(player.isPresent()) {
                    ProxyDiscord.inst().getDebugLogger().info("Linked account is currently on the server. Adding permission");
                    luckpermsManager.addVerifiedPermission(player.get());
                }

                status = VerificationResult.VERIFIED;
            } else {
                if(player.isPresent()) {
                    ProxyDiscord.inst().getDebugLogger().info("Linked account is currently on the server. Removing permission");
                    luckpermsManager.removeVerifiedPermission(player.get(), RemovalReason.VERIFIED_ROLE_LOST);
                }

                status = VerificationResult.LINKED_NOT_VERIFIED;
            }
        }

        ProxyDiscord.inst().getDebugLogger().info("Status for Discord id " + discordId.toString() + ": " + status);

        player.ifPresent(p -> updatePlayerStatus(p, status));

        return status;
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

    private void updatePlayerStatus(Player player, VerificationResult status) {
        lastKnownStatuses.compute(player, (key, value) -> {
            if(value != null && value != status) {
                ProxyDiscord.inst().getDebugLogger().info("PlayerVerifyStateChangeEvent " + status + " from " + value);
                proxy.getEventManager().fireAndForget(new PlayerVerifyStateChangeEvent(player, status, value));
            }

            return status;
        });
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

    public Set<RegisteredServer> getPublicServers() {
        return publicServers;
    }

    public boolean isPublicServer(RegisteredServer server) {
        return publicServers.contains(server);
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