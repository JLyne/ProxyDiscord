package me.prouser123.bungee.discord;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
//import me.glaremasters.deluxequeues.DeluxeQueues;
import me.glaremasters.deluxequeues.DeluxeQueues;
import me.prouser123.bungee.discord.events.PlayerUnverifiedEvent;
import me.prouser123.bungee.discord.events.PlayerVerifiedEvent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import ninja.leaping.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class VerificationManager {
    private final LinkingManager linkingManager;
    private final KickManager kickManager;

    private final String verifiedPermission;
    private final String bypassPermission;
    private final String verifiedRoleId;
    private final RegisteredServer unverifiedServer;

    private final ArrayList<Long> verifiedRoleUsers;

    private final ProxyServer proxy;
    private final Logger logger;

    public VerificationManager(ConfigurationNode config) {
        this.proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();

        verifiedRoleUsers = new ArrayList<>();
        linkingManager = ProxyDiscord.inst().getLinkingManager();
        kickManager = ProxyDiscord.inst().getKickManager();

        verifiedRoleId = config.getNode("verify-role-id").getString();
        verifiedPermission = config.getNode("verified-permission").getString();
        bypassPermission = config.getNode("bypass-permission").getString();

        String unverifiedServerName = config.getNode("unverified-server").getString();
        unverifiedServer = proxy.getServer(unverifiedServerName).orElse(null);

        if(unverifiedServer == null && unverifiedServerName != null && !unverifiedServerName.isEmpty()) {
            logger.warn("Unverified server (" + unverifiedServerName + ") does not exist!");
        }

        if(verifiedRoleId != null) {
            populateUsers();
        }

        ProxyDiscord.inst().getDiscord().getApi().addReconnectListener(event -> {
            if(verifiedRoleId != null) {
                populateUsers();
            }
        });
    }

    public void addUser(User user) {
        if (!verifiedRoleUsers.contains(user.getId())) {
            verifiedRoleUsers.add(user.getId());
        }

        String linked = linkingManager.getLinked(user);
        ProxyDiscord.inst().getDebugLogger().info("Removing verified status of " + user.getDiscriminatedName());

        if(linked != null) {
            Optional<Player> player = proxy.getPlayer(UUID.fromString(linked));

            if(player.isPresent()) {
                ProxyDiscord.inst().getDebugLogger().info("Linked account is currently on the server. Adding permission");
                addVerifiedPermission(player.get());
            }
        }
    }

    public void removeUser(User user) {
        verifiedRoleUsers.remove(user.getId());

        String linked = linkingManager.getLinked(user);
        ProxyDiscord.inst().getDebugLogger().info("Removing verified status of " + user.getDiscriminatedName());

        if(linked != null) {
            Optional<Player> player  = proxy.getPlayer(UUID.fromString(linked));

            if(player.isPresent()) {
                ProxyDiscord.inst().getDebugLogger().info("Linked account is currently on the server. Removing permission");
                removeVerifiedPermission(player.get(), RemovalReason.VERIFIED_ROLE_LOST);
            }
        }
    }

    public boolean hasVerifiedRole(Long id) {
        return verifiedRoleUsers.contains(id);
    }

    public VerificationResult checkVerificationStatus(Player player) {
        if(verifiedRoleId == null) {
            ProxyDiscord.inst().getDebugLogger().info("No verified role defined. Considering " + player.getUsername() + " verified.");
            addVerifiedPermission(player);

            return VerificationResult.VERIFIED;
        }

        if(player.hasPermission(bypassPermission)) {
            ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " has bypass permission. Epic.");
            addVerifiedPermission(player);

            return VerificationResult.VERIFIED;
        }

        Long linkedId = linkingManager.getLinked(player);

        if (linkedId != null) {
            if(hasVerifiedRole(linkedId)) {
                ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " has linked discord and has verified role. Epic.");
                addVerifiedPermission(player);

                return VerificationResult.VERIFIED;
            }

            ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " has linked discord, but doesn't have the verified role. Sad.");
            removeVerifiedPermission(player, RemovalReason.VERIFIED_ROLE_LOST);

            return VerificationResult.LINKED_NOT_VERIFIED;
        }

        ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " hasn't linked discord. Very sad.");
        removeVerifiedPermission(player, RemovalReason.UNLINKED);

        return VerificationResult.NOT_LINKED;
    }

    VerificationResult checkVerificationStatus(Long discordId) {
        if(verifiedRoleId == null) {
            ProxyDiscord.inst().getDebugLogger().info("No verified role defined. Considering " + discordId.toString() + " verified.");
            return VerificationResult.VERIFIED;
        }

        UUID linked = linkingManager.getLinked(discordId);

        if(linked == null) {
            ProxyDiscord.inst().getDebugLogger().info("Discord id " + discordId.toString() + " has not been linked to a player. Sad.");
            return VerificationResult.NOT_LINKED;
        }

        Optional<Player> player = proxy.getPlayer(linked);

        if(player.isPresent()) {
            if(player.get().hasPermission(bypassPermission)) {
                ProxyDiscord.inst().getDebugLogger().info("Player " + player.get().getUsername() + " has bypass permission. Epic.");
                addVerifiedPermission(player.get());

                return VerificationResult.VERIFIED;
            }
        }

        if(hasVerifiedRole(discordId)) {
            ProxyDiscord.inst().getDebugLogger().info("Discord id " + discordId.toString() + " is linked and has verified role. Epic.");

            if(player.isPresent()) {
                ProxyDiscord.inst().getDebugLogger().info("Linked account is currently on the server. Adding permission");
                addVerifiedPermission(player.get());
            }

            return VerificationResult.VERIFIED;
        } else {
            ProxyDiscord.inst().getDebugLogger().info("Discord id " + discordId.toString() + " is linked, but doesn't have the verified role. Sad.");

            if(player.isPresent()) {
                ProxyDiscord.inst().getDebugLogger().info("Linked account is currently on the server. Removing permission");
                removeVerifiedPermission(player.get(), RemovalReason.VERIFIED_ROLE_LOST);
            }

            return VerificationResult.LINKED_NOT_VERIFIED;
        }
    }

    private void addVerifiedPermission(Player player) {
        if(player.hasPermission(verifiedPermission)) {
            return;
        }

        kickManager.removePlayer(player);

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

        proxy.getEventManager().fireAndForget(new PlayerVerifiedEvent(player));
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

        TextComponent.Builder message;

        switch(reason) {
            case VERIFIED_ROLE_LOST:
                message = TextComponent.builder().content(ChatMessages.getMessage("verification-lost-role"));
                break;

            case UNLINKED:
                message = TextComponent.builder().content(ChatMessages.getMessage("verification-lost-unlinked"));
                break;
            default:
                message = TextComponent.builder().content("Something unexpected happened.");
        }

        message.color(NamedTextColor.RED);

        if(unverifiedServer == null) {
            ProxyDiscord.inst().getDebugLogger().info("No unverified server defined. Kicking " + player.getUsername());
            player.disconnect(message.build());

            return;
        }

        //Remove player from any queues
        Optional<PluginContainer> deluxeQueues = proxy.getPluginManager().getPlugin("deluxequeues");

        deluxeQueues.ifPresent(
                plugin -> ((DeluxeQueues) plugin.getInstance().get()).getQueueHandler().clearPlayer(player));

        Optional<ServerConnection> currentServer = player.getCurrentServer();

        if(currentServer.isPresent() && !currentServer.get().getServer().equals(unverifiedServer)) {
            ProxyDiscord.inst().getDebugLogger().info("Moving " + player.getUsername() + " to " + unverifiedServer.getServerInfo().getName());

            player.createConnectionRequest(unverifiedServer).connect().thenAccept(result -> {
                if(!result.isSuccessful()) {
                    String text = ChatMessages.getMessage("verification-lost-moved");
                    TextComponent extra = TextComponent.of(text.replace("[server]", unverifiedServer.getServerInfo().getName()));
                    message.append(extra);

                    player.sendMessage(message.build());
                } else {
                    ProxyDiscord.inst().getDebugLogger().info("Failed to move " + player.getUsername() + " to " + unverifiedServer.getServerInfo().getName() + ". Kicking.");
                    player.disconnect(message.build());
                }
            });

            player.sendMessage(message.build());
        } else if(reason != RemovalReason.UNLINKED) {
            player.sendMessage(message.build());
        }

        proxy.getEventManager().fireAndForget(new PlayerUnverifiedEvent(player));
        kickManager.addPlayer(player);
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

        for(User user: users) {
            verifiedRoleUsers.add(user.getId());
        }
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
}
