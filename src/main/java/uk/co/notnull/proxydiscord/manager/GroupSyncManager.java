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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.spongepowered.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.api.events.PlayerLinkEvent;
import uk.co.notnull.proxydiscord.api.events.PlayerUnlinkEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class GroupSyncManager implements uk.co.notnull.proxydiscord.api.manager.GroupSyncManager {
	private final Logger logger;
	private final ProxyServer proxy;
	private final LinkingManager linkingManager;
	private final LuckPermsManager luckPermsManager;

	private final Map<Long, Set<String>> syncSettings;
	private final Set<Long> syncedRoles;
	private final Set<String> groups;
	private final Map<Role, Set<Long>> roleUsers;

	private final VerificationManager verificationManager;
	private final ProxyDiscord plugin;

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
		ConfigurationNode settings = config.node("synced-roles");

		groups.clear();
		syncedRoles.clear();
		syncSettings.clear();

		if (!settings.empty()) {
			Map<Object, ? extends ConfigurationNode> syncSettingsMap = settings.childrenMap();

			syncSettingsMap.forEach((Object role, ConfigurationNode roleConfig) -> {
				long roleId;
				Set<String> groupNames;

				try {
					roleId = Long.parseLong(role.toString());
				} catch (NumberFormatException e) {
					logger.warn("Ignoring synced role '{}': Invalid role ID", role);
					return;
				}

				if (roleConfig.empty()) {
					logger.warn("Ignoring synced role '{}': No groups assigned", role);
					return;
				}

				if (roleConfig.isList()) {
					groupNames = new HashSet<>();
					List<? extends ConfigurationNode> children = roleConfig.childrenList();

					children.forEach((ConfigurationNode child) -> {
						if (!child.empty() && !child.isMap() && !child.isList()) {
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
		}
	}

	public void populateUsers(Guild guild) {
		removeUsers(guild, false);

		syncedRoles.forEach((Long roleId) -> {
			Role role = guild.getRoleById(roleId);

			if (role == null) {
				logger.warn("Failed to load role ({}). Is the ID incorrect or is discord down?", roleId);

				return;
			}

			Set<Long> userSet = ConcurrentHashMap.newKeySet();

			logger.info("Role syncing enabled for role {}", role.getName());

			List<Member> members = role.getGuild().getMembersWithRoles(role);

			for (Member member : members) {
				userSet.add(member.getIdLong());
			}

			roleUsers.put(role, userSet);
		});

        proxy.getAllPlayers().forEach(this::syncPlayer);
	}

	public void removeUsers(Guild guild) {
		removeUsers(guild, true);
	}

	public void removeUsers(Guild guild, boolean updateUsers) {
		roleUsers.keySet().removeIf(role -> role.getGuild().equals(guild));

		if (updateUsers) {
			proxy.getAllPlayers().forEach(this::syncPlayer);
		}
	}

	@Subscribe()
    public void onPlayerLink(PlayerLinkEvent event) {
        event.getPlayer().ifPresent(this::syncPlayer);
    }

    @Subscribe()
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
			roleUsers.forEach((Role role, Set<Long> users) -> {
				if (users.contains(discordId)) {
					groups.addAll(syncSettings.get(role.getIdLong()));
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
		return syncedRoles.contains(role.getIdLong());
	}

	public void handleRoleRemove(User user, List<Role> roles) {
		UUID uuid = linkingManager.getLinked(user);
		boolean changed = false;

		if (uuid == null) {
			return;
		}

		for(Role role : roles) {
			if(!syncedRoles.contains(role.getIdLong())) {
				continue;
			}

			// Remove synced role
			changed = changed || roleUsers.get(role).remove(user.getIdLong());
		}

		if(changed) {
			proxy.getPlayer(uuid).ifPresent(this::syncPlayer);
		}
	}

	public void handleRoleAdd(User user, List<Role> roles) {
		UUID uuid = linkingManager.getLinked(user);
		boolean changed = false;

		if (uuid == null) {
			return;
		}

		for(Role role : roles) {
			if(!syncedRoles.contains(role.getIdLong())) {
				continue;
			}

			// Add synced role
			changed = changed || roleUsers.get(role).add(user.getIdLong());
		}

		if(changed) {
			proxy.getPlayer(uuid).ifPresent(this::syncPlayer);
		}
	}

	public void handleMemberRemove(User user, Guild guild) {
		UUID uuid = linkingManager.getLinked(user);
		boolean changed = false;

		if (uuid == null) {
			return;
		}

		//Remove from all synced roles for the guild
		for (Map.Entry<Role, Set<Long>> entry : roleUsers.entrySet()) {
			Role role = entry.getKey();
			Set<Long> users = entry.getValue();
			if (role.getGuild().equals(guild)) {
				users.remove(user.getIdLong());
				changed = true;
			}
		}

		if(changed) {
			proxy.getPlayer(uuid).ifPresent(this::syncPlayer);
		}
	}

	public void handleMemberJoin(Member member) {
		UUID uuid = linkingManager.getLinked(member.getUser());

		if (uuid == null) {
			return;
		}

		//Add to all synced roles the member has
		member.getRoles().forEach((Role role) -> {
			if(isSyncedRole(role)) {
				roleUsers.get(role).add(member.getIdLong());
			}
		});

		proxy.getPlayer(uuid).ifPresent(this::syncPlayer);
	}

	public void reload(ConfigurationNode config) {
        parseConfig(config);

		for (Guild guild : plugin.getDiscord().getJDA().getGuilds()) {
            populateUsers(guild);
        }
    }
}