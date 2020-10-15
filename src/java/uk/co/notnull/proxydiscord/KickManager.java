package uk.co.notnull.proxydiscord;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.events.PlayerVerifyStateChangeEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class KickManager {
    private final ConcurrentHashMap<Player, Long> kickablePlayers;
    private final int kickTime;

    private final ProxyServer proxy;
    private final Logger logger;

    public KickManager(int kickTime) {
        this.proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();

        kickablePlayers = new ConcurrentHashMap<>();
        this.kickTime = kickTime;

        this.kickPlayers();
        proxy.getEventManager().register(ProxyDiscord.inst(), this);
    }

    public void addPlayer(Player player) {
        if(kickTime <= 0) {
            return;
        }

        Long previous = kickablePlayers.putIfAbsent(player, System.currentTimeMillis());

        if(previous == null) {
            ProxyDiscord.inst().getDebugLogger().info("Added player " + player.getUsername() + " to kickable list");
        }
    }

    public void removePlayer(Player player) {
        if(kickablePlayers.remove(player) != null) {
            ProxyDiscord.inst().getDebugLogger().info("Removed player " + player.getUsername() + " from kickable list");
        }
    }

    private void kickPlayers() {
        proxy.getScheduler().buildTask(ProxyDiscord.inst(), () -> {

        }).delay(10, TimeUnit.SECONDS).repeat(10, TimeUnit.SECONDS).schedule();
    }

    private void kickTask() {
        ProxyDiscord.inst().getDebugLogger().info("Running kick task");

        Long now = System.currentTimeMillis();
        VerificationManager verificationManager = ProxyDiscord.inst().getVerificationManager();

        Iterator iterator = kickablePlayers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry pair = (Map.Entry) iterator.next();
            Long addedTime = (Long) pair.getValue();
            Player player = (Player) pair.getKey();
            TextComponent message;

            if(!player.isActive()) {
                iterator.remove();
                continue;
            }

            ProxyDiscord.inst().getDebugLogger().info("Player " + player.getUsername() + " elapsed time " + (now - addedTime) / 1000 + ". Kick time: " + kickTime);

            if(((now - addedTime) / 1000) > kickTime) {
                switch(verificationManager.checkVerificationStatus(player)) {
                    case VERIFIED:
                        continue;

                    case LINKED_NOT_VERIFIED:
                        message = Component.text(ChatMessages.getMessage("kicked-linked-not-verified")).color(
                                NamedTextColor.RED);
                        break;
                    case NOT_LINKED:
                    default:
                        message = Component.text(ChatMessages.getMessage("kicked-not-linked")).color(NamedTextColor.RED);
                }

                ProxyDiscord.inst().getDebugLogger().info("Kicking player " + player.getUsername() + " for exceeding unverified kick time");
                iterator.remove();
                player.disconnect(message);
            }
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayerVerifyStateChange(PlayerVerifyStateChangeEvent event) {
        if(event.getState() == VerificationResult.VERIFIED) {
            removePlayer(event.getPlayer());
        } else {
            addPlayer(event.getPlayer());
        }
    }
}
