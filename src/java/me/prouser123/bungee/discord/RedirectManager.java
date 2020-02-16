package me.prouser123.bungee.discord;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.prouser123.bungee.discord.events.PlayerVerifiedEvent;
import org.slf4j.Logger;

import java.util.*;

public class RedirectManager {
    private final VerificationManager verificationManager;
    private HashMap<UUID, RegisteredServer> destinations;

    private final ProxyServer proxy;
    private final Logger logger;

    public RedirectManager() {
        this.proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();
        this.verificationManager = ProxyDiscord.inst().getVerificationManager();

        destinations = new HashMap<>();
        proxy.getEventManager().register(ProxyDiscord.inst(), this);
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPlayerConnect(ServerPreConnectEvent event) {
        Optional<RegisteredServer> redirectServer = event.getResult().getServer();

        if(redirectServer.isPresent() && redirectServer.get().equals(verificationManager.getUnverifiedServer())) {
            destinations.put(event.getPlayer().getUniqueId(), event.getOriginalServer());
        } else {
            destinations.remove(event.getPlayer().getUniqueId());
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayerVerified(PlayerVerifiedEvent event) {
        Player player = event.getPlayer();

        if(destinations.containsKey(player.getUniqueId())) {
            player.createConnectionRequest(destinations.get(player.getUniqueId())).fireAndForget();
        }
    }
}
