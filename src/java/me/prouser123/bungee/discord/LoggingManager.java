package me.prouser123.bungee.discord;

import me.lucko.luckperms.LuckPerms;
import me.lucko.luckperms.api.Contexts;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.User;
import me.lucko.luckperms.api.caching.MetaData;
import me.lucko.luckperms.api.manager.UserManager;
import me.prouser123.bungee.discord.bot.commands.ServerInfo;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.MessageDecoration;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggingManager implements Listener {
    private final String loggingChannelId;
    private final Integer lockDummy = 0;

    private AtomicInteger logsPerMessage = new AtomicInteger(1); //Number of logs to combine together into one message, to avoid rate limits and falling behind
    private AtomicInteger unsentLogs = new AtomicInteger(0); //Number of unsent logs in current message
    private AtomicInteger queuedToSend = new AtomicInteger(0); //Number of messages waiting to be sent by javacord

    private MessageBuilder currentMessage; //Current unsent message
    private LuckPermsApi luckPermsApi;
    private ListenerManager<MessageCreateListener> logListener = null;

    LoggingManager(String loggingChannelId) {
        this.loggingChannelId = loggingChannelId;
        currentMessage = new MessageBuilder();

        luckPermsApi = LuckPerms.getApi();

        if(loggingChannelId != null) {
            findChannel();
        }

        Main.inst().getProxy().getPluginManager().registerListener(Main.inst(), this);

        Main.inst().getDiscord().getApi().addReconnectListener(event -> {
            if(loggingChannelId != null) {
                findChannel();
            }
        });

        //Decrease logs per message if a low number of messages are unsent
        Main.inst().getProxy().getScheduler().schedule(Main.inst(), () -> {
            if(queuedToSend.get() <= 2 && logsPerMessage.get() > 1) {
                Main.inst().getLogger().info("Decreasing logsPerMessage due to low activity (" + queuedToSend.get() + " queued messages)");
                logsPerMessage.set(Math.max(logsPerMessage.get() / 2, 1));
            }
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

        sendLogMessage(getPlayerLogName(sender) + "\n" + message);
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

        if(logListener != null) {
            logListener.remove();
        }

        logListener = loggingChannel.get().addMessageCreateListener(event -> {
            if(event.getMessageAuthor().isYourself()) {
                return;
            }

            Main.inst().getProxy().getScheduler().runAsync(Main.inst(), () -> {
                String message = event.getReadableMessageContent();
                Long discordId = event.getMessage().getAuthor().getId();
                String linked = Main.inst().getLinkingManager().getLinked(discordId);

                event.deleteMessage();

                if(linked != null) {
                    sendDiscordMessage(UUID.fromString(linked), message);
                }
            });
        });

        Main.inst().getLogger().info("Activity logging enabled for channel: #" + loggingChannel.toString().replaceAll(".*\\[|].*", "") + " (id: " + loggingChannelId + ")");
    }

    private String getPlayerLogName(ProxiedPlayer player) {
        Long discordId = Main.inst().getLinkingManager().getLinked(player);
        Server server = player.getServer();
        String serverName = server != null ? server.getInfo().getName() : "none";

        if(discordId != null) {
            return "[" + serverName + "][" + player.getName() + "](<@!" + discordId.toString() + ">)";
        } else {
            return "[" + serverName + "][" + player.getName() + "](Unlinked)";
        }
    }

    private String getPlayerLogName(User user) {
         Long discordId = Main.inst().getLinkingManager().getLinked(user.getUuid());

        if(discordId != null) {
            return "[" + user.getName() + "](<@!" + discordId.toString() + ">)";
        } else {
            return "[" + user.getName() + "](Unlinked)";
        }
    }

    private void sendDiscordMessage(UUID uuid, String message) {
        UserManager userManager = luckPermsApi.getUserManager();
        User user = userManager.loadUser(uuid).join();

        if(user == null) {
            return;
        }

        if(message.isEmpty()) {
            return;
        }

        MetaData metaData = user.getCachedData().getMetaData(Contexts.allowAll());
        String prefix = ( metaData.getPrefix() != null) ?  metaData.getPrefix() : "";
        String suffix = ( metaData.getSuffix() != null) ?  metaData.getSuffix() : "";

        String text = "&l&bDISCORD>&r " + prefix + user.getName() + suffix + "&r: " + message;
        text = ChatColor.translateAlternateColorCodes('&', text);

        BaseComponent[] components = TextComponent.fromLegacyText(text);
        Main.inst().getProxy().broadcast(components);
        sendLogMessage("[DISCORD]" + getPlayerLogName(user) + "\n" + message);
    }

    private void sendLogMessage(String message) {
        if(loggingChannelId == null) {
            return;
        }

        String dateFormat = "<dd-MM-yy HH:mm:ss> ";
        SimpleDateFormat format = new SimpleDateFormat(dateFormat);
        String date = format.format(new Date());
        Optional <TextChannel> loggingChannel = Main.inst().getDiscord().getApi().getTextChannelById(loggingChannelId);

        message = date + message;

        synchronized (lockDummy) {
            if(currentMessage.getStringBuilder().length() + message.length() > 1950) {

                if(loggingChannel.isPresent()) {
                    queuedToSend.incrementAndGet();
                    currentMessage.send(loggingChannel.get()).thenAcceptAsync(result -> {
                        queuedToSend.decrementAndGet();
                    }).exceptionally(error -> {
                        Main.inst().getLogger().warning("Failed to send log message");
                        queuedToSend.decrementAndGet();
                        return null;
                    });
                }

                currentMessage = new MessageBuilder();
                unsentLogs.set(0);
            }

            currentMessage.append(MessageDecoration.CODE_LONG.getPrefix())
                    .append("md").append("\n").append(message).append(MessageDecoration.CODE_LONG.getSuffix());

            if(unsentLogs.incrementAndGet() >= logsPerMessage.get()) {

                if(loggingChannel.isPresent()) {
                    queuedToSend.incrementAndGet();
                    currentMessage.send(loggingChannel.get()).thenAcceptAsync(result -> {
                        queuedToSend.decrementAndGet();
                    }).exceptionally(error -> {
                        Main.inst().getLogger().warning("Failed to send log message");
                        queuedToSend.decrementAndGet();
                        return null;
                    });
                }

                currentMessage = new MessageBuilder();
                unsentLogs.set(0);
            }

            //Increase logsPerMessage if many messages are queued, to help stop the log falling behind
            if(queuedToSend.get() >= 5 && logsPerMessage.get() < 16) {
                Main.inst().getLogger().info("Increasing logsPerMessage due to high activity (" + queuedToSend.get() + " queued messages)");
                logsPerMessage.set(Math.min(logsPerMessage.get() * 2, 16));
            }
        }
    }
}
