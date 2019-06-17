package me.prouser123.bungee.discord;

import com.google.common.collect.HashBiMap;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class KickManager {
    private HashBiMap<Long, ProxiedPlayer> kickablePlayers;
    private int kickTime;

    KickManager(int kickTime) {
        kickablePlayers = HashBiMap.create(64);
        this.kickTime = kickTime;

        this.kickPlayers();
    }

    public void addPlayer(ProxiedPlayer player) {
        if(kickTime <= 0) {
            return;
        }

        if (!kickablePlayers.containsValue(player)) {
            Main.inst().getLogger().info("Adding player " + player.getName() + " to kickable list");

            kickablePlayers.put(System.currentTimeMillis(), player);
        }
    }

    public void removePlayer(ProxiedPlayer player) {
        Main.inst().getLogger().info("Removing player " + player.getName() + " from kickable list");
        kickablePlayers.inverse().remove(player);
    }

    private void kickPlayers() {
        Main.inst().getProxy().getScheduler().schedule(Main.inst(), () -> {
            Main.inst().getDebugLogger().info("Running kick task");

            Long now = System.currentTimeMillis();
            VerificationManager verificationManager = Main.inst().getVerificationManager();

            Iterator iterator = kickablePlayers.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry pair = (Map.Entry) iterator.next();
                ProxiedPlayer player = (ProxiedPlayer) pair.getValue();
                Long addedTime =(Long) pair.getKey();
                BaseComponent[] message;

                if(!player.isConnected()) {
                    iterator.remove();
                    continue;
                }

                Main.inst().getDebugLogger().info("Player " + player.getName() + " added time " + addedTime);
                Main.inst().getDebugLogger().info("Player " + player.getName() + " elapsed time " + (now - addedTime) / 1000);
                Main.inst().getDebugLogger().info("Kick time: " + kickTime);

                if(((now - addedTime) / 1000) > kickTime) {
                    switch(verificationManager.checkVerificationStatus(player)) {
                        case VERIFIED:
                            iterator.remove();
                            continue;

                        case LINKED_NOT_VERIFIED:
                            message = new ComponentBuilder(ChatMessages.getMessage("kicked-linked-not-verified")).color(ChatColor.RED).create();
                            break;
                        case NOT_LINKED:
                        default:
                            message = new ComponentBuilder(ChatMessages.getMessage("kicked-not-linked")).color(ChatColor.RED).create();
                    }

                    Main.inst().getDebugLogger().info("Kicking player " + player.getName() + " for exceeding unverified kick time");
                    player.disconnect(message);
                    iterator.remove();
                }
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
}
