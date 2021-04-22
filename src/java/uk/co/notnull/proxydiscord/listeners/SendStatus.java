package uk.co.notnull.proxydiscord.listeners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import uk.co.notnull.platformdetection.PlatformDetectionVelocity;
import uk.co.notnull.proxydiscord.*;
import uk.co.notnull.proxydiscord.events.PlayerVerifyStateChangeEvent;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public class SendStatus {
	private static VerificationManager verificationManager;
	private static LinkingManager linkingManager;

	public SendStatus() {
        SendStatus.verificationManager = ProxyDiscord.inst().getVerificationManager();
        SendStatus.linkingManager = ProxyDiscord.inst().getLinkingManager();
    }

	@Subscribe(order = PostOrder.FIRST)
    public void onServerPostConnect(ServerPostConnectEvent e) {
		ProxyDiscord.inst().getDebugLogger().info("ServerPostConnectEvent");
		sendStatusPacket(e.getPlayer(), verificationManager.checkVerificationStatus(e.getPlayer()));
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPlayerVerifyStatusChange(PlayerVerifyStateChangeEvent e) {
		ProxyDiscord.inst().getDebugLogger().info("PlayerVerifyStateChangeEvent");
		if(e.getState() == VerificationResult.VERIFIED) {
			e.getPlayer().sendMessage(Identity.nil(), Component.text(ChatMessages.getMessage("link-success"))
											 .color(NamedTextColor.GREEN));
		} else {
			sendStatusPacket(e.getPlayer(), e.getState());
		}
	}

    private void sendStatusPacket(Player player, VerificationResult status) {
		ProxyDiscord.inst().getDebugLogger().info("sendStatusPacket");

        player.getCurrentServer().ifPresent(connection -> {
        	ProxyDiscord.inst().getDebugLogger().info(connection.getServer().getServerInfo().getName());

        	if(!verificationManager.isLinkingServer(connection.getServer())) {
        		return;
			}

            try {
                final byte[] byteKey = linkingManager.getLinkingSecret().getBytes(StandardCharsets.UTF_8);
                Mac hmac = Mac.getInstance("HmacSHA512");
                SecretKeySpec keySpec = new SecretKeySpec(byteKey, "HmacSHA512");
                hmac.init(keySpec);

                boolean bedrock = false;
                if(ProxyDiscord.inst().isPlatformDetectionEnabled()) {
					PlatformDetectionVelocity platformDetection = ProxyDiscord.inst().getPlatformDetectionHandler()
							.getPlatformDetection();
					bedrock = platformDetection.getPlatform(player).isBedrock();
				}

                String token = linkingManager.getLinkingToken(player);

                byte[] macData = hmac.doFinal(String.format("%s%s%s", status.ordinal(), bedrock ? 1 : 0, token)
                                                      .getBytes(StandardCharsets.UTF_8));

                Map<String, Object> payload = Map.of(
                        "hmac", byteArrayToHex(macData),
                        "status", status.ordinal(),
                        "bedrock", bedrock,
                        "token", token);

                Gson gson = new GsonBuilder().create();
                ProxyDiscord.inst().getLogger().info("Sending status packet");
                connection.sendPluginMessage(ProxyDiscord.getStatusIdentifier(), gson.toJson(payload).getBytes());
            } catch(NoSuchAlgorithmException | InvalidKeyException e) {
                ProxyDiscord.inst().getLogger().error("Failed to generate status packet for " + player.getUsername());
                e.printStackTrace();
            }
        });
    }

    private static String byteArrayToHex(byte[] a) {
       StringBuilder sb = new StringBuilder(a.length * 2);
       for(byte b: a)
          sb.append(String.format("%02x", b));
       return sb.toString();
    }
}
