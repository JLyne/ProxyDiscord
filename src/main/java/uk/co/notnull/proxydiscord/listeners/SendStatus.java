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

package uk.co.notnull.proxydiscord.listeners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import uk.co.notnull.proxydiscord.Messages;
import uk.co.notnull.proxydiscord.manager.LinkingManager;
import uk.co.notnull.proxydiscord.ProxyDiscord;
import uk.co.notnull.proxydiscord.manager.VerificationManager;
import uk.co.notnull.proxydiscord.api.VerificationResult;
import uk.co.notnull.proxydiscord.api.events.PlayerVerifyStateChangeEvent;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public class SendStatus {
	private final ProxyDiscord plugin;
	private final VerificationManager verificationManager;
	private final LinkingManager linkingManager;

	public SendStatus(ProxyDiscord plugin) {
		this.plugin = plugin;
        verificationManager = plugin.getVerificationManager();
        linkingManager = plugin.getLinkingManager();
    }

	@Subscribe(priority = Short.MAX_VALUE - 1)
    public void onServerPostConnect(ServerPostConnectEvent e) {
		sendStatusPacket(e.getPlayer(), verificationManager.checkVerificationStatus(e.getPlayer()));
    }

    @Subscribe(priority = Short.MIN_VALUE + 1)
    public void onPlayerVerifyStatusChange(PlayerVerifyStateChangeEvent e) {
		if(e.getState() == VerificationResult.VERIFIED && !e.getPreviousState().isVerified()
				&& e.getPreviousState() != VerificationResult.UNKNOWN) {
			Messages.sendComponent(e.getPlayer(), "link-success");
		} else if(!e.getState().isVerified()) {
			sendStatusPacket(e.getPlayer(), e.getState());
		}
	}

    private void sendStatusPacket(Player player, VerificationResult status) {
		player.getCurrentServer().ifPresent(connection -> {
        	if(!verificationManager.isLinkingServer(connection.getServer())) {
        		return;
			}

            try {
                final byte[] byteKey = linkingManager.getLinkingSecret().getBytes(StandardCharsets.UTF_8);
                Mac hmac = Mac.getInstance("HmacSHA512");
                SecretKeySpec keySpec = new SecretKeySpec(byteKey, "HmacSHA512");
                hmac.init(keySpec);

                boolean bedrock = false;

                if(plugin.isPlatformDetectionEnabled()) {
					bedrock = plugin.getPlatformDetectionHandler().isBedrock(player);
				}

                String token = linkingManager.getLinkingToken(player);

                byte[] macData = hmac.doFinal(String.format("%s%s%s", status.ordinal(), bedrock ? 1 : 0, token)
                                                      .getBytes(StandardCharsets.UTF_8));

                Map<String, Object> payload = Map.of(
                        "hmac", byteArrayToHex(macData),
                        "status", status.ordinal(), //FIXME: Don't use magic numbers
                        "bedrock", bedrock,
                        "token", token);

                Gson gson = new GsonBuilder().create();
		   		connection.sendPluginMessage(ProxyDiscord.getStatusIdentifier(), gson.toJson(payload).getBytes());
            } catch(NoSuchAlgorithmException | InvalidKeyException e) {
                plugin.getLogger().error("Failed to generate status packet for " + player.getUsername());
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
