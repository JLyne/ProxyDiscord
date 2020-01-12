package me.prouser123.bungee.discord.bot.commands;

import java.util.ArrayList;
import java.util.List;

import com.velocitypowered.api.proxy.Player;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import me.prouser123.bungee.discord.ProxyDiscord;

public class Players implements MessageCreateListener, BaseCommand {

    private final base base;

    public Players(int priority, String command, String helpText) {
        base = this.easyBaseSetup(priority, command, helpText);
    }

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        if (event.getMessage().getContent().equalsIgnoreCase(base.command)) {

            EmbedBuilder embed2 = new EmbedBuilder()
                    .setTitle("Online Players")
                    .setDescription("All players currently online on the network.")
                    .setFooter("Bungee Discord | !bd"/*.split("-")[0]*/, "https://cdn.discordapp.com/avatars/215119410103451648/575d90fdda8663b633e36f8b8c06c719.png");

            // Create an array of players and their servers
            List<String> players = new ArrayList<>();

            for (Player player : ProxyDiscord.inst().getProxy().getAllPlayers()) {
                players.add(player.getUsername() + " at " + player.getCurrentServer().get().getServerInfo().getName());
            }

            players.sort(String.CASE_INSENSITIVE_ORDER);

            if (players.size() == 0) {
                embed2.setDescription("There are no players online.");
            }

            // Create a field in the embed for every player online
            for (String player : players) {
                String[] playerArray = player.split(" at ");
                // Player name is item 0, Player server is item 1
                embed2.addField(playerArray[0], "online at " + playerArray[1]);
            }

            // Send the embed
            event.getChannel().sendMessage(embed2);
        }
    }
}