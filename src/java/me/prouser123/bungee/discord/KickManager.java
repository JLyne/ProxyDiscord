package me.prouser123.bungee.discord;

import com.google.common.collect.HashBiMap;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class KickManager {
    private final HashBiMap<Long, Player> kickablePlayers;
    private final int kickTime;

    private final ProxyServer proxy;
    private final Logger logger;

    public KickManager(int kickTime) {
        this.proxy = Main.inst().getProxy();
        this.logger = Main.inst().getLogger();

        kickablePlayers = HashBiMap.create(64);
        this.kickTime = kickTime;

        this.kickPlayers();
    }

    public void addPlayer(Player player) {
        if(kickTime <= 0) {
            return;
        }

        if (!kickablePlayers.containsValue(player)) {
            Main.inst().getDebugLogger().info("Adding player " + player.getUsername() + " to kickable list");

            kickablePlayers.put(System.currentTimeMillis(), player);
        }
    }

    public void removePlayer(Player player) {
        Main.inst().getDebugLogger().info("Removing player " + player.getUsername() + " from kickable list");
        kickablePlayers.inverse().remove(player);
    }

    private void kickPlayers() {
        proxy.getScheduler().buildTask(Main.inst(), () -> {
            Main.inst().getDebugLogger().info("Running kick task");

            Long now = System.currentTimeMillis();
            VerificationManager verificationManager = Main.inst().getVerificationManager();

            Iterator iterator = kickablePlayers.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry pair = (Map.Entry) iterator.next();
                Player player = (Player) pair.getValue();
                Long addedTime =(Long) pair.getKey();
                TextComponent message;

                if(!player.isActive()) {
                    iterator.remove();
                    continue;
                }

                Main.inst().getDebugLogger().info("Player " + player.getUsername() + " added time " + addedTime);
                Main.inst().getDebugLogger().info("Player " + player.getUsername() + " elapsed time " + (now - addedTime) / 1000);
                Main.inst().getDebugLogger().info("Kick time: " + kickTime);

                if(((now - addedTime) / 1000) > kickTime) {
                    switch(verificationManager.checkVerificationStatus(player)) {
                        case VERIFIED:
                            iterator.remove();
                            continue;

                        case LINKED_NOT_VERIFIED:
                            message = TextComponent.of(ChatMessages.getMessage("kicked-linked-not-verified")).color(
                                    TextColor.RED);
                            break;
                        case NOT_LINKED:
                        default:
                            message = TextComponent.of(ChatMessages.getMessage("kicked-not-linked")).color(TextColor.RED);
                    }

                    Main.inst().getDebugLogger().info("Kicking player " + player.getUsername() + " for exceeding unverified kick time");
                    player.disconnect(message);
                    iterator.remove();
                }
            }
        }).delay(10, TimeUnit.SECONDS).repeat(10, TimeUnit.SECONDS).schedule();
    }
}
