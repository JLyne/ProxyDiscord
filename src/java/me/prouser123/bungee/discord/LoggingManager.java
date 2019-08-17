package me.prouser123.bungee.discord;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.MessageDecoration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggingManager implements Listener {
    private final String loggingChannelId;
    private final Integer lockDummy = 0;

    private AtomicInteger logsPerMessage = new AtomicInteger(1); //Number of logs to combine together into one message, to avoid rate limits and falling behind
    private AtomicInteger unsentLogs = new AtomicInteger(0); //Number of unsent logs in current message
    private AtomicInteger sentSinceLastCheck = new AtomicInteger(0); //Number of messages sent since last check

    private MessageBuilder currentMessage; //Current unsent message

    LoggingManager(String loggingChannelId) {
        this.loggingChannelId = loggingChannelId;
        currentMessage = new MessageBuilder();

        if(loggingChannelId != null) {
            findChannel();
        }

        Main.inst().getProxy().getPluginManager().registerListener(Main.inst(), this);

        Main.inst().getDiscord().getApi().addReconnectListener(event -> {
            if(loggingChannelId != null) {
                findChannel();
            }
        });

        //Check messages that were sent every 5 seconds, and adjust logsPerMessage accordingly
        //Ideally it will strike a balance between the log falling behind due to rate limits, and the chat not updating often enough due to lots of logs in one message
        Main.inst().getProxy().getScheduler().schedule(Main.inst(), () -> {
            if(sentSinceLastCheck.get() >= 5 && logsPerMessage.get() < 16) {
                Main.inst().getLogger().info("Increasing logsPerMessage due to high activity (" + sentSinceLastCheck.get() + " logs)");
                logsPerMessage.set(Math.min(logsPerMessage.get() * 2, 16));
            }

            if(sentSinceLastCheck.get() <= 2 && logsPerMessage.get() > 1) {
                Main.inst().getLogger().info("Decreasing logsPerMessage due to low activity (" + sentSinceLastCheck.get() + " logs)");
                logsPerMessage.set(Math.max(logsPerMessage.get() / 2, 1));
            }

            sentSinceLastCheck.set(0);
        }, 5, 5, TimeUnit.SECONDS);
    }

    public void logJoin(ProxiedPlayer player) {
        sendLogMessage(getPlayerLogName(player) + " has joined the network.");
    }

    public void logLeave(ProxiedPlayer player) {
        sendLogMessage(getPlayerLogName(player) + " has left the network.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(ChatEvent e) {
        if(e.isCancelled()) {
            return;
        }

        if(!(e.getSender() instanceof ProxiedPlayer)) {
            return;
        }

        ProxiedPlayer sender = (ProxiedPlayer) e.getSender();
        String message = e.getMessage().replace("```", "");

        sendLogMessage(getPlayerLogName(sender) + ": " + message);
    }

    private void findChannel() {
        if(loggingChannelId == null) {
            return;
        }

        Optional <TextChannel> loggingChannel = Main.inst().getDiscord().getApi().getTextChannelById(loggingChannelId);

        if(!loggingChannel.isPresent()) {
            Main.inst().getLogger().warning("Unable to find logging channel. Did you put a valid channel ID in the config?");
            return;
        }

        Main.inst().getLogger().info("Activity logging enabled for channel: #" + loggingChannel.toString().replaceAll(".*\\[|].*", "") + " (id: " + loggingChannelId + ")");
    }

    private String getPlayerLogName(ProxiedPlayer player) {
         Long discordId = Main.inst().getLinkingManager().getLinked(player);

        if(discordId != null) {
            return "[" + player.getName() + "](<@!" + discordId.toString() + ">)";
        } else {
            return "[" + player.getName() + "](Unlinked)";
        }
    }

    private void sendLogMessage(String message) {
        if(loggingChannelId == null) {
            return;
        }

        String dateFormat = "<dd-MM-yy HH:mm:ss>";
        SimpleDateFormat format = new SimpleDateFormat(dateFormat);
        String date = format.format(new Date());
        Optional <TextChannel> loggingChannel = Main.inst().getDiscord().getApi().getTextChannelById(loggingChannelId);

        message = date + message;

        synchronized (lockDummy) {
            if(currentMessage.getStringBuilder().length() + message.length() > 1950) {
                loggingChannel.ifPresent(textChannel ->  currentMessage.send(textChannel));
                currentMessage = new MessageBuilder();

                unsentLogs.set(0);
                sentSinceLastCheck.incrementAndGet();
            }

            currentMessage.append(MessageDecoration.CODE_LONG.getPrefix())
                    .append("md").append("\n").append(message).append(MessageDecoration.CODE_LONG.getSuffix());

            if(unsentLogs.incrementAndGet() >= logsPerMessage.get()) {
                loggingChannel.ifPresent(textChannel ->  currentMessage.send(textChannel));
                currentMessage = new MessageBuilder();

                unsentLogs.set(0);
                sentSinceLastCheck.incrementAndGet();
            }
        }

    }
}
