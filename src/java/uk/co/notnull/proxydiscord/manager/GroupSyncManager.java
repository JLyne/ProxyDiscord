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

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import ninja.leaping.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.member.ServerMemberBanEvent;
import org.javacord.api.event.server.member.ServerMemberEvent;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;
import org.javacord.api.event.server.member.ServerMemberLeaveEvent;
import org.javacord.api.event.server.role.UserRoleAddEvent;
import org.javacord.api.event.server.role.UserRoleEvent;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.api.events.PlayerLinkEvent;
import uk.co.notnull.proxydiscord.api.events.PlayerUnlinkEvent;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class GroupSyncManager implements uk.co.notnull.proxydiscord.api.manager.GroupSyncManager {
	private final ProxyDiscord plugin;
	private final Logger logger;
	private final ProxyServer proxy;
	private final LinkingManager linkingManager;
	private final LuckPermsManager luckPermsManager;

	private final Map<Long, Set<Long>> roleUsers;
	private final Map<Long, Set<String>> syncSettings;

	private final Set<Long> syncedRoles;
	private final Set<String> groups;
	private final VerificationManager verificationManager;

	public GroupSyncManager(ProxyDiscord plugin, ConfigurationNode config) {
		this.plugin = plugin;
		this.logger = plugin.getLogger();
		this.proxy = plugin.getProxy();
		this.linkingManager = plugin.getLinkingManager();
		this.verificationManager = plugin.getVerificationManager();
		this.luckPermsManager = plugin.getLuckpermsManager();

		roleUsers = new ConcurrentHashMap<>();
		syncSettings = new HashMap<>();

		syncedRoles = new HashSet<>();
		groups = new HashSet<>();

		parseConfig(config);
	}

	private void parseConfig(ConfigurationNode config) {
		ConfigurationNode settings = config.getNode("synced-roles");

		groups.clear();
		syncedRoles.clear();
		syncSettings.clear();

		if (!settings.isEmpty()) {
			Map<Object, ? extends ConfigurationNode> syncSettingsMap = settings.getChildrenMap();

			syncSettingsMap.forEach((Object role, ConfigurationNode roleConfig) -> {
				long roleId;
				Set<String> groupNames;

				try {
					roleId = Long.parseLong(role.toString());
				} catch (NumberFormatException e) {
					logger.warn("Ignoring synced role '" + role + "': Invalid role ID");
					return;
				}

				if (roleConfig.isEmpty()) {
					logger.warn("Ignoring synced role '" + role + "': No groups assigned");
					return;
				}

				if (roleConfig.isList()) {
					groupNames = new HashSet<>();
					List<? extends ConfigurationNode> children = roleConfig.getChildrenList();

					children.forEach((ConfigurationNode child) -> {
						if (!child.isEmpty() && !child.isMap() && !child.isList()) {
							groupNames.add(child.getString());
						}
					});
				} else {
					groupNames = Collections.singleton(roleConfig.getString());
				}

				groups.addAll(groupNames);
				syncedRoles.add(roleId);
				syncSettings.put(roleId, groupNames);
			});

			populateUsers();
		}
	}

	public void populateUsers() {
		roleUsers.clear();

		syncedRoles.forEach((Long roleId) -> {
			Optional<Role> role = plugin.getDiscord().getApi().getRoleById(roleId);

			if (role.isEmpty()) {
				logger.warn("Failed to load role (" + roleId + "). Is the ID incorrect or is discord down?");

				return;
			}

			Set<Long> userSet = ConcurrentHashMap.newKeySet();

			logger.info("Role syncing enabled for role " + role.get().getName());

			Collection<User> users = role.get().getUsers();

			for (User user : users) {
				userSet.add(user.getId());
			}

			roleUsers.put(roleId, userSet);
		});

        proxy.getAllPlayers().forEach(this::syncPlayer);
	}

	@Subscribe(order = PostOrder.NORMAL)
    public void onPlayerLink(PlayerLinkEvent event) {
        event.getPlayer().ifPresent(this::syncPlayer);
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayerUnlink(PlayerUnlinkEvent event) {
        event.getPlayer().ifPresent(this::syncPlayer);
    }

	/**
	 * Determines which synced groups a player belongs in, based on their linked discord account's roles.
	 * Players without a linked account will have all synced roles removed.
	 * @param player - The player to sync
	 * @return Completable future
	 */
	public @NonNull CompletableFuture<Void> syncPlayer(Player player) {
		Long discordId = linkingManager.getLinked(player);
		Set<String> groups = new HashSet<>();

		if (discordId != null) {
			//Get set of synced groups the player should be in
			roleUsers.forEach((Long roleId, Set<Long> users) -> {
				if (users.contains(discordId)) {
					groups.addAll(syncSettings.get(roleId));
				}
			});
		}

		return luckPermsManager.updateUserGroups(player, groups, this.groups)
				.thenAccept((Boolean changed) -> {
					if(changed) {
						verificationManager.checkVerificationStatus(player);
					}
				});
	}

	public boolean isSyncedRole(Role role) {
		return syncedRoles.contains(role.getId());
	}

	public void handleRoleEvent(UserRoleEvent userRoleEvent) {
		Role role = userRoleEvent.getRole();
		User user = userRoleEvent.getUser();

		if (!isSyncedRole(role)) {
			return;
		}

		UUID uuid = linkingManager.getLinked(user);

		if (uuid == null) {
			return;
		}

		Optional<Player> player = proxy.getPlayer(uuid);

		if (player.isEmpty()) {
			return;
		}

		if (userRoleEvent instanceof UserRoleAddEvent) {
			// Add synced role
			roleUsers.get(role.getId()).add(user.getId());
		} else {
			// Remove synced role
			roleUsers.get(role.getId()).remove(user.getId());
		}

		syncPlayer(player.get());
	}

	public void handleServerMemberEvent(ServerMemberEvent event) {
		UUID uuid = linkingManager.getLinked(event.getUser());
		User user = event.getUser();

		if (uuid == null) {
			return;
		}

		Optional<Player> player = proxy.getPlayer(uuid);

		if (player.isEmpty()) {
			return;
		}

		if(event instanceof ServerMemberLeaveEvent || event instanceof ServerMemberBanEvent) {
			//Remove from all synced roles
			roleUsers.forEach((Long roleId, Set<Long> users) -> users.remove(user.getId()));
			luckPermsManager.updateUserGroups(player.get(), Collections.emptySet(), this.groups)
					.thenAccept((Boolean changed) -> {
						if (changed) {
							verificationManager.checkVerificationStatus(player.get());
						}
					});
		} else if(event instanceof ServerMemberJoinEvent) {
			//Add to all synced roles the user has
			user.getRoles(event.getServer()).forEach((Role role) -> {
				if(isSyncedRole(role)) {
					roleUsers.get(role.getId()).add(user.getId());
				}
			});
			syncPlayer(player.get());
		}
	}

	public void reload(ConfigurationNode config) {
        parseConfig(config);
    }
}