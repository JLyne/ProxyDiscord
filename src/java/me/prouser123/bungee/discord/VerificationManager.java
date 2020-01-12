package me.prouser123.bungee.discord;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
//import me.glaremasters.deluxequeues.DeluxeQueues;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import ninja.leaping.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;

import javax.inject.Inject;
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
        this.proxy = Main.inst().getProxy();
        this.logger = Main.inst().getLogger();

        verifiedRoleUsers = new ArrayList<>();
        linkingManager = Main.inst().getLinkingManager();
        kickManager = Main.inst().getKickManager();

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

        Main.inst().getDiscord().getApi().addReconnectListener(event -> {
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
        Main.inst().getDebugLogger().info("Removing verified status of " + user.getDiscriminatedName());

        if(linked != null) {
            Optional<Player> player = proxy.getPlayer(UUID.fromString(linked));

            if(player.isPresent()) {
                Main.inst().getDebugLogger().info("Linked account is currently on the server. Adding permission");
                addVerifiedPermission(player.get());
            }
        }
    }

    public void removeUser(User user) {
        verifiedRoleUsers.remove(user.getId());

        String linked = linkingManager.getLinked(user);
        Main.inst().getDebugLogger().info("Removing verified status of " + user.getDiscriminatedName());

        if(linked != null) {
            Optional<Player> player  = proxy.getPlayer(UUID.fromString(linked));

            if(player.isPresent()) {
                Main.inst().getDebugLogger().info("Linked account is currently on the server. Removing permission");
                removeVerifiedPermission(player.get(), RemovalReason.VERIFIED_ROLE_LOST);
            }
        }
    }

    public boolean hasVerifiedRole(Long id) {
        return verifiedRoleUsers.contains(id);
    }

    public VerificationResult checkVerificationStatus(Player player) {
        if(verifiedRoleId == null) {
            Main.inst().getDebugLogger().info("No verified role defined. Considering " + player.getUsername() + " verified.");
            return VerificationResult.VERIFIED;
        }

        if(player.hasPermission(bypassPermission)) {
            Main.inst().getDebugLogger().info("Player " + player.getUsername() + " has bypass permission. Epic.");
            addVerifiedPermission(player);

            return VerificationResult.VERIFIED;
        }

        Long linkedId = linkingManager.getLinked(player);

        if (linkedId != null) {
            if(hasVerifiedRole(linkedId)) {
                Main.inst().getDebugLogger().info("Player " + player.getUsername() + " has linked discord and has verified role. Epic.");
                addVerifiedPermission(player);

                return VerificationResult.VERIFIED;
            }

            Main.inst().getDebugLogger().info("Player " + player.getUsername() + " has linked discord, but doesn't have the verified role. Sad.");
            removeVerifiedPermission(player, RemovalReason.VERIFIED_ROLE_LOST);

            return VerificationResult.LINKED_NOT_VERIFIED;
        }

        Main.inst().getDebugLogger().info("Player " + player.getUsername() + " hasn't linked discord. Very sad.");
        removeVerifiedPermission(player, RemovalReason.UNLINKED);

        return VerificationResult.NOT_LINKED;
    }

    VerificationResult checkVerificationStatus(Long discordId) {
        if(verifiedRoleId == null) {
            Main.inst().getDebugLogger().info("No verified role defined. Considering " + discordId.toString() + " verified.");
            return VerificationResult.VERIFIED;
        }

        String linked = linkingManager.getLinked(discordId);

        if(linked == null) {
            Main.inst().getDebugLogger().info("Discord id " + discordId.toString() + " has not been linked to a player. Sad.");
            return VerificationResult.NOT_LINKED;
        }

        Optional<Player> player = proxy.getPlayer(UUID.fromString(linked));

        if(player.isPresent()) {
            if(player.get().hasPermission(bypassPermission)) {
                Main.inst().getDebugLogger().info("Player " + player.get().getUsername() + " has bypass permission. Epic.");
                addVerifiedPermission(player.get());

                return VerificationResult.VERIFIED;
            }
        }

        if(hasVerifiedRole(discordId)) {
            Main.inst().getDebugLogger().info("Discord id " + discordId.toString() + " is linked and has verified role. Epic.");

            if(player.isPresent()) {
                Main.inst().getDebugLogger().info("Linked account is currently on the server. Adding permission");
                addVerifiedPermission(player.get());
            }

            return VerificationResult.VERIFIED;
        } else {
            Main.inst().getDebugLogger().info("Discord id " + discordId.toString() + " is linked, but doesn't have the verified role. Sad.");

            if(player.isPresent()) {
                Main.inst().getDebugLogger().info("Linked account is currently on the server. Removing permission");
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

        Main.inst().getDebugLogger().info("Adding verified permission to " + player.getUsername());

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

        Main.inst().getDebugLogger().info("Removing verified permission from " + player.getUsername());

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

        message.color(TextColor.RED);

        if(unverifiedServer == null) {
            Main.inst().getDebugLogger().info("No unverified server defined. Kicking " + player.getUsername());
            player.disconnect(message.build());

            return;
        }

        //TODO: DeluxeQueues
        //Remove player from any queues
//        DeluxeQueues deluxeQueues = (DeluxeQueues) proxy.getPluginManager().getPlugin("DeluxeQueues");
//
//        if(deluxeQueues != null) {
//            deluxeQueues.getQueueHandler().clearPlayer(player);
//        }

        Optional<ServerConnection> currentServer = player.getCurrentServer();

        if(currentServer.isPresent() && !currentServer.get().getServer().equals(unverifiedServer)) {
            Main.inst().getDebugLogger().info("Moving " + player.getUsername() + " to " + unverifiedServer.getServerInfo().getName());

            player.createConnectionRequest(unverifiedServer).connect().thenAccept(result -> {
                if(!result.isSuccessful()) {
                    String text = ChatMessages.getMessage("verification-lost-moved");
                    TextComponent extra = TextComponent.of(text.replace("[server]", unverifiedServer.getServerInfo().getName()));
                    message.append(extra);

                    player.sendMessage(message.build());
                } else {
                    Main.inst().getDebugLogger().info("Failed to move " + player.getUsername() + " to " + unverifiedServer.getServerInfo().getName() + ". Kicking.");
                    player.disconnect(message.build());
                }
            });

            player.sendMessage(message.build());
        } else if(reason != RemovalReason.UNLINKED) {
            player.sendMessage(message.build());
        }

        kickManager.addPlayer(player);
    }

    public void populateUsers() {
        Optional<Role> verifiedRole = Main.inst().getDiscord().getApi().getRoleById(verifiedRoleId);

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
        Optional<Role> role = Main.inst().getDiscord().getApi().getRoleById(verifiedRoleId);

        return role.orElse(null);
    }

    public RegisteredServer getUnverifiedServer() {
        return unverifiedServer;
    }
}
