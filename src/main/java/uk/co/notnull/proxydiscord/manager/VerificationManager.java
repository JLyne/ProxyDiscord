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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.spongepowered.configurate.ConfigurationNode;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.api.VerificationResult;
import uk.co.notnull.proxydiscord.api.events.PlayerLinkEvent;
import uk.co.notnull.proxydiscord.api.events.PlayerUnlinkEvent;
import uk.co.notnull.proxydiscord.api.events.PlayerVerifyStateChangeEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VerificationManager implements uk.co.notnull.proxydiscord.api.manager.VerificationManager {
    private final ProxyDiscord plugin;
    private final LinkingManager linkingManager;

    private final Set<RegisteredServer> publicServers;
    private RegisteredServer defaultVerifiedServer;
    private RegisteredServer linkingServer;

    private String bypassPermission;
    private Set<Long> verifiedRoleIds;
    private final ConcurrentHashMap<Role, Set<Long>> verifiedRoleUsers;

    private final ProxyServer proxy;
    private final Logger logger;
    private final ConcurrentHashMap<Player, VerificationResult> lastKnownStatuses;

    public VerificationManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();

        verifiedRoleUsers = new ConcurrentHashMap<>();
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
                            verifiedRoleIds.add(Long.valueOf(roleId));
                        } catch(NumberFormatException e) {
							logger.warn("Ignoring verified role '{}': Invalid role ID", roleId);
                        }
                    }
                });
            } else {
                String roleId = roleIds.getString();

                try {
                    verifiedRoleIds = Collections.singleton(Long.valueOf(roleId));
                } catch(NumberFormatException e) {
					logger.warn("Ignoring verified role '{}': Invalid role ID", roleId);
                    verifiedRoleIds = Collections.emptySet();
                }
            }
        }

        bypassPermission = config.node("linking", "bypass-permission").getString();
        String linkingServerName = config.node("linking", "linking-server").getString();
        linkingServer = proxy.getServer(linkingServerName).orElse(null);

        publicServers.clear();

        if(linkingServer == null && linkingServerName != null && !linkingServerName.isEmpty()) {
			logger.warn("Linking server ({}) does not exist!", linkingServerName);
        } else if(linkingServer != null) {
            publicServers.add(linkingServer);
        }

        List<? extends ConfigurationNode> publicServerNames = config.node("linking", "public-servers").childrenList();

        for(ConfigurationNode serverName : publicServerNames) {
            String name = serverName.getString();
            Optional<RegisteredServer> server = proxy.getServer(name);

            server.ifPresent(publicServers::add);

            if(server.isEmpty() && name != null && !name.isEmpty()) {
				logger.warn("Public server ({}) does not exist!", name);
            }
        }

        String defaultVerifiedServerName = config.node("linking", "default-destination-server").getString();
        defaultVerifiedServer = proxy.getServer(defaultVerifiedServerName).orElse(null);

        if(defaultVerifiedServer == null && defaultVerifiedServerName != null && !defaultVerifiedServerName.isEmpty()) {
			logger.warn("Default verified server ({}) does not exist!", defaultVerifiedServerName);
        }
    }

    @Subscribe()
    public void onPlayerLink(PlayerLinkEvent event) {
        event.getPlayer().ifPresent(this::checkVerificationStatus);
    }

    @Subscribe()
    public void onPlayerUnlink(PlayerUnlinkEvent event) {
        event.getPlayer().ifPresent(this::checkVerificationStatus);
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
                if(verifiedRoleUsers.values().stream().anyMatch(s -> s.contains(linkedId))) {
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

    public VerificationResult checkVerificationStatus(User user) {
        return checkVerificationStatus(user.getIdLong());
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
            } else if(verifiedRoleUsers.values().stream().anyMatch(s -> s.contains(discordId))) {
                status = VerificationResult.VERIFIED;
            } else {
                status = VerificationResult.LINKED_NOT_VERIFIED;
            }
        }

        plugin.getDebugLogger().info("Verification status for Discord id " + discordId.toString() + ": " + status);

        player.ifPresent(p -> updatePlayerStatus(p, status));

        return status;
    }

    public void populateUsers(Guild guild) {
        removeUsers(guild, false);

        verifiedRoleIds.forEach((Long roleId) -> {
            Role verifiedRole = guild.getJDA().getRoleById(roleId);

            if(verifiedRole == null) {
				//logger.warn("Failed to load verified role ({}). Is the ID incorrect or is discord down?)", roleId);
                return;
            }

			logger.info("Role verification enabled for role {} in {}", verifiedRole.getName(), guild.getName());

            List<Member> members = verifiedRole.getGuild().getMembersWithRoles(verifiedRole);
            Set<Long> value = verifiedRoleUsers.computeIfAbsent(verifiedRole, _ -> ConcurrentHashMap.newKeySet());
            value.clear();

            for(Member member: members) {
                value.add(member.getIdLong());
            }
        });

        proxy.getAllPlayers().forEach(this::checkVerificationStatus);
    }

    public void removeUsers(Guild guild) {
        removeUsers(guild, true);
    }

    public void removeUsers(Guild guild, boolean updatePlayers) {
        verifiedRoleUsers.keySet().removeIf(role -> role.getGuild().equals(guild));

        if (updatePlayers) {
            proxy.getAllPlayers().forEach(this::checkVerificationStatus);
        }
    }

    private void updatePlayerStatus(Player player, VerificationResult status) {
        lastKnownStatuses.compute(player, (_, value) -> {
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

    public void handleRoleAdd(User user, List<Role> roles) {
        AtomicBoolean changed = new AtomicBoolean(false);

        for (Role role : roles) {
            verifiedRoleUsers.computeIfPresent(role, (_, value) -> {
                value.add(user.getIdLong());
                changed.set(true);
                return value;
            });
        }

        if (changed.get()) {
            checkVerificationStatus(user);
        }
	}

	public void handleRoleRemove(User user, List<Role> roles) {
        AtomicBoolean changed = new AtomicBoolean(false);

        for (Role role : roles) {
            verifiedRoleUsers.computeIfPresent(role, (_, value) -> {
                if (value.remove(user.getIdLong())) {
                    changed.set(true);
                }
                return value;
            });
        }

        if (changed.get()) {
            checkVerificationStatus(user);
        }
	}

    public Set<Role> getVerifiedRoles() {
        return Collections.unmodifiableSet(verifiedRoleUsers.keySet());
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

        for (Guild guild : plugin.getDiscord().getJDA().getGuilds()) {
            populateUsers(guild);
        }
    }
}
