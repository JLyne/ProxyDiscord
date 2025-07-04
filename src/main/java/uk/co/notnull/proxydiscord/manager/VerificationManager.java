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
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.spongepowered.configurate.ConfigurationNode;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.member.ServerMemberBanEvent;
import org.javacord.api.event.server.member.ServerMemberEvent;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;
import org.javacord.api.event.server.member.ServerMemberLeaveEvent;
import org.javacord.api.event.server.role.UserRoleEvent;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.api.VerificationResult;
import uk.co.notnull.proxydiscord.api.events.PlayerLinkEvent;
import uk.co.notnull.proxydiscord.api.events.PlayerUnlinkEvent;
import uk.co.notnull.proxydiscord.api.events.PlayerVerifyStateChangeEvent;

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

public final class VerificationManager implements uk.co.notnull.proxydiscord.api.manager.VerificationManager {
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

        parseConfig(config);
    }

    private void parseConfig(ConfigurationNode config) {
        ConfigurationNode roleIds = config.node("linking", "verified-role-ids");

        verifiedRoleIds = new HashSet<>();
        lastKnownStatuses.clear();

        if(!roleIds.empty()) {
            if(roleIds.isList()) {

                List<? extends ConfigurationNode> children = roleIds.childrenList();

                children.forEach((ConfigurationNode child) -> {
                    if(!child.empty() && !child.isMap() && !child.isList()) {
                        String roleId = child.getString();

                        try {
                            verifiedRoleIds.add(Long.parseLong(roleId));
                        } catch(NumberFormatException e) {
                            logger.warn("Ignoring verified role '" + roleId + "': Invalid role ID");
                        }
                    }
                });
            } else {
                String roleId = roleIds.getString();

                try {
                    verifiedRoleIds = Collections.singleton(Long.parseLong(roleId));
                } catch(NumberFormatException e) {
                    logger.warn("Ignoring verified role '" + roleId + "': Invalid role ID");
                    verifiedRoleIds = Collections.emptySet();
                }
            }
        }

        bypassPermission = config.node("linking", "bypass-permission").getString();
        String linkingServerName = config.node("linking", "linking-server").getString();
        linkingServer = proxy.getServer(linkingServerName).orElse(null);

        publicServers.clear();

        if(linkingServer == null && linkingServerName != null && !linkingServerName.isEmpty()) {
            logger.warn("Linking server (" + linkingServerName + ") does not exist!");
        } else if(linkingServer != null) {
            publicServers.add(linkingServer);
        }

        List<? extends ConfigurationNode> publicServerNames = config.node("linking", "public-servers").childrenList();

        for(ConfigurationNode serverName : publicServerNames) {
            String name = serverName.getString();
            Optional<RegisteredServer> server = proxy.getServer(name);

            server.ifPresent(publicServers::add);

            if(server.isEmpty() && name != null && !name.isEmpty()) {
                logger.warn("Public server (" + name + ") does not exist!");
            }
        }

        String defaultVerifiedServerName = config.node("linking", "default-destination-server").getString();
        defaultVerifiedServer = proxy.getServer(defaultVerifiedServerName).orElse(null);

        if(defaultVerifiedServer == null && defaultVerifiedServerName != null && !defaultVerifiedServerName.isEmpty()) {
            logger.warn("Default verified server (" + defaultVerifiedServerName + ") does not exist!");
        }

        populateUsers();
    }

    @Subscribe()
    public void onPlayerLink(PlayerLinkEvent event) {
        event.getPlayer().ifPresent(this::checkVerificationStatus);
    }

    @Subscribe()
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
