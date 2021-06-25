package uk.co.notnull.proxydiscord.manager;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import ninja.leaping.configurate.ConfigurationNode;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.member.ServerMemberBanEvent;
import org.javacord.api.event.server.member.ServerMemberEvent;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;
import org.javacord.api.event.server.member.ServerMemberLeaveEvent;
import org.javacord.api.event.server.role.UserRoleEvent;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.VerificationResult;
import uk.co.notnull.proxydiscord.events.PlayerLinkEvent;
import uk.co.notnull.proxydiscord.events.PlayerUnlinkEvent;
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
    private final ProxyDiscord plugin;
    private final LinkingManager linkingManager;

    private final Set<RegisteredServer> publicServers;
    private RegisteredServer defaultVerifiedServer;
    private RegisteredServer linkingServer;

    private String bypassPermission;
    private Set<Long> verifiedRoleIds;

    private final Set<Long> verifiedRoleUsers;

    private final ProxyServer proxy;
    private final Logger logger;
    private final ConcurrentHashMap<Player, VerificationResult> lastKnownStatuses;

    public VerificationManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();

        verifiedRoleUsers = ConcurrentHashMap.newKeySet();
        linkingManager = plugin.getLinkingManager();
        lastKnownStatuses = new ConcurrentHashMap<>();
        publicServers = new HashSet<>();

        proxy.getEventManager().register(plugin, this);
        parseConfig(config);
    }

    private void parseConfig(ConfigurationNode config) {
        ConfigurationNode roleIds = config.getNode("linking", "verified-role-ids");

        verifiedRoleIds = new HashSet<>();
        lastKnownStatuses.clear();

        if(!roleIds.isEmpty()) {
            if(roleIds.isList()) {

                List<? extends ConfigurationNode> children = roleIds.getChildrenList();

                children.forEach((ConfigurationNode child) -> {
                    if(!child.isEmpty() && !child.isMap() && !child.isList()) {
                        String roleId = child.getString(null);

                        try {
                            verifiedRoleIds.add(Long.parseLong(roleId));
                        } catch(NumberFormatException e) {
                            logger.warn("Ignoring verified role '" + roleId + "': Invalid role ID");
                        }
                    }
                });
            } else {
                String roleId = roleIds.getString(null);

                try {
                    verifiedRoleIds = Collections.singleton(Long.parseLong(roleId));
                } catch(NumberFormatException e) {
                    logger.warn("Ignoring verified role '" + roleId + "': Invalid role ID");
                    verifiedRoleIds = Collections.emptySet();
                }
            }
        }

        bypassPermission = config.getNode("linking", "bypass-permission").getString();
        String linkingServerName = config.getNode("linking", "linking-server").getString();
        linkingServer = proxy.getServer(linkingServerName).orElse(null);

        publicServers.clear();

        if(linkingServer == null && linkingServerName != null && !linkingServerName.isEmpty()) {
            logger.warn("Linking server (" + linkingServerName + ") does not exist!");
        } else if(linkingServer != null) {
            publicServers.add(linkingServer);
        }

        List<? extends ConfigurationNode> publicServerNames = config.getNode("linking", "public-servers").getChildrenList();

        for(ConfigurationNode serverName : publicServerNames) {
            String name = serverName.getString();
            Optional<RegisteredServer> server = proxy.getServer(name);

            server.ifPresent(publicServers::add);

            if(server.isEmpty() && name != null && !name.isEmpty()) {
                logger.warn("Public server (" + name + ") does not exist!");
            }
        }

        String defaultVerifiedServerName = config.getNode("linking", "default-destination-server").getString();
        defaultVerifiedServer = proxy.getServer(defaultVerifiedServerName).orElse(null);

        if(defaultVerifiedServer == null && defaultVerifiedServerName != null && !defaultVerifiedServerName.isEmpty()) {
            logger.warn("Default verified server (" + defaultVerifiedServerName + ") does not exist!");
        }

        populateUsers();
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayerLink(PlayerLinkEvent event) {
        event.getPlayer().ifPresent(this::checkVerificationStatus);
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayerUnlink(PlayerUnlinkEvent event) {
        event.getPlayer().ifPresent(this::checkVerificationStatus);
    }

    private void addUser(User user) {
        verifiedRoleUsers.add(user.getId());
        checkVerificationStatus(user.getId());
    }

    private void removeUser(User user) {
        verifiedRoleUsers.remove(user.getId());
        checkVerificationStatus(user.getId());
    }

    public VerificationResult checkVerificationStatus(Player player) {
        VerificationResult status;

        if(verifiedRoleIds.isEmpty()) {
            status = VerificationResult.NOT_REQUIRED;
        } else if(player.hasPermission(bypassPermission)) {
            status = VerificationResult.BYPASSED;
        } else {
            Long linkedId = linkingManager.getLinked(player);

            if (linkedId != null) {
                if(verifiedRoleUsers.contains(linkedId)) {
                    status = VerificationResult.VERIFIED;
                } else {
                    status = VerificationResult.LINKED_NOT_VERIFIED;
                }
            } else {
                status = VerificationResult.NOT_LINKED;
            }
        }

        plugin.getDebugLogger().info("Verification status for player " + player.getUsername() + ": " + status);

        updatePlayerStatus(player, status);

        return status;
    }

    public VerificationResult checkVerificationStatus(Long discordId) {
        VerificationResult status;

        UUID linked = linkingManager.getLinked(discordId);
        Optional<Player> player = Optional.empty();

        if(verifiedRoleIds.isEmpty()) {
            status = VerificationResult.NOT_REQUIRED;
        } else if(linked == null) {
            status = VerificationResult.NOT_LINKED;
        } else {
            player = proxy.getPlayer(linked);

            if(player.isPresent() && player.get().hasPermission(bypassPermission)) {
                status = VerificationResult.BYPASSED;
            } else if(verifiedRoleUsers.contains(discordId)) {
                status = VerificationResult.VERIFIED;
            } else {
                status = VerificationResult.LINKED_NOT_VERIFIED;
            }
        }

        plugin.getDebugLogger().info("Verification status for Discord id " + discordId.toString() + ": " + status);

        player.ifPresent(p -> updatePlayerStatus(p, status));

        return status;
    }

    public void populateUsers() {
        verifiedRoleUsers.clear();

        verifiedRoleIds.forEach((Long roleId) -> {
            Optional<Role> verifiedRole = plugin.getDiscord().getApi().getRoleById(roleId);

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
            if(value == null) {
                proxy.getEventManager().fireAndForget(new PlayerVerifyStateChangeEvent(player, status));
            } else if(value != status) {
                plugin.getDebugLogger().info("PlayerVerifyStateChangeEvent " + status + " from " + value);
                proxy.getEventManager().fireAndForget(new PlayerVerifyStateChangeEvent(player, status, value));
            }

            return status;
        });
    }

    public void clearPlayerStatus(Player player) {
		lastKnownStatuses.remove(player);
	}

	public void handleRoleEvent(UserRoleEvent userRoleEvent) {
		Role role = userRoleEvent.getRole();
		User user = userRoleEvent.getUser();

		if (!isVerifiedRole(role)) {
			return;
		}

		List<Role> roles = user.getRoles(userRoleEvent.getServer());

        if (Collections.disjoint(roles, getVerifiedRoles())) {
            removeUser(user);
        } else {
            addUser(user);
        }
	}

	public void handleServerMemberEvent(ServerMemberEvent event) {
		User user = event.getUser();

		if(event instanceof ServerMemberLeaveEvent || event instanceof ServerMemberBanEvent) {
			removeUser(user);
		} else if(event instanceof ServerMemberJoinEvent) { //TODO: Double check
		    List<Role> roles = user.getRoles(event.getServer());

            if(getVerifiedRoles().isEmpty()) {
                return;
            }

			if(!Collections.disjoint(roles, getVerifiedRoles())) {
			    addUser(user);
            }
		}
	}

    public boolean isVerifiedRole(Role role) {
        return verifiedRoleIds.contains(role.getId());
	}

    public Set<Role> getVerifiedRoles() {
        return verifiedRoleIds.stream().map((Long roleId) -> {
            Optional<Role> role = plugin.getDiscord().getApi().getRoleById(roleId);

            return role.orElse(null);
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public Set<RegisteredServer> getPublicServers() {
        return publicServers;
    }

    public boolean isPublicServer(RegisteredServer server) {
        return server != null && publicServers.contains(server);
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

    public void reload(ConfigurationNode config) {
        parseConfig(config);
    }
}
