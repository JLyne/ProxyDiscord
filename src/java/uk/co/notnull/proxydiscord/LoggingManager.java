package uk.co.notnull.proxydiscord;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.MessageDecoration;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;
import org.slf4j.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggingManager {
    private final String loggingChannelId;
    private final Integer lockDummy = 0;

    private final AtomicInteger logsPerMessage = new AtomicInteger(1); //Number of logs to combine together into one message, to avoid rate limits and falling behind
    private final AtomicInteger unsentLogs = new AtomicInteger(0); //Number of unsent logs in current message
    private final AtomicInteger queuedToSend = new AtomicInteger(0); //Number of messages waiting to be sent by javacord

    private MessageBuilder currentMessage; //Current unsent message
    private ListenerManager<MessageCreateListener> logListener = null;

    private final ProxyServer proxy;
    private final Logger logger;

    public LoggingManager(String loggingChannelId) {
        this.proxy = ProxyDiscord.inst().getProxy();
        this.logger = ProxyDiscord.inst().getLogger();

        this.loggingChannelId = loggingChannelId;
        currentMessage = new MessageBuilder();

        if(loggingChannelId != null) {
            findChannel();
        }

        proxy.getEventManager().register(ProxyDiscord.inst(), this);

        ProxyDiscord.inst().getDiscord().getApi().addReconnectListener(event -> {
            if(loggingChannelId != null) {
                findChannel();
            }
        });

        //Decrease logs per message if a low number of messages are unsent
        proxy.getScheduler().buildTask(ProxyDiscord.inst(), () -> {
            if(queuedToSend.get() <= 2 && logsPerMessage.get() > 1) {
                logger.info("Decreasing logsPerMessage due to low activity (" + queuedToSend.get() + " queued messages)");
                logsPerMessage.set(Math.max(logsPerMessage.get() / 2, 1));
            }
        }).repeat( 5, TimeUnit.SECONDS).delay( 5, TimeUnit.SECONDS).schedule();
    }

    public void logJoin(Player player) {
        sendLogMessage(getPlayerLogName(player) + " has joined the network.");
    }

    public void logLeave(Player player) {
        sendLogMessage(getPlayerLogName(player) + " has left the network.");
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPlayerChat(PlayerChatEvent e) {
        if(!e.getResult().isAllowed()) {
            return;
        }

        Player sender = e.getPlayer();
        String message = e.getMessage().replace("```", "");

        sendLogMessage("[CHAT] " + getPlayerLogName(sender) + "\n" + message);
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPlayerCommand(CommandExecuteEvent e) {
        if(!e.getResult().isAllowed()) {
            return;
        }

        if(!(e.getCommandSource() instanceof Player)) {
            return;
        }

        Player sender = (Player) e.getCommandSource();
        String message = e.getCommand().replace("```", "");

        sendLogMessage("[COMMAND] " + getPlayerLogName(sender) + "\n/" + message);
    }

    private void findChannel() {
        if(loggingChannelId == null) {
            return;
        }

        Optional <TextChannel> loggingChannel = ProxyDiscord.inst().getDiscord().getApi().getTextChannelById(loggingChannelId);

        if(loggingChannel.isEmpty()) {
            logger.warn("Unable to find logging channel. Did you put a valid channel ID in the config?");
            return;
        }

        if(logListener != null) {
            logListener.remove();
        }

        logListener = loggingChannel.get().addMessageCreateListener(event -> {
            if(event.getMessageAuthor().isYourself()) {
                return;
            }

            proxy.getScheduler().buildTask(ProxyDiscord.inst(), () -> {
                String message = event.getReadableMessageContent();
                Long discordId = event.getMessage().getAuthor().getId();
                UUID linked = ProxyDiscord.inst().getLinkingManager().getLinked(discordId);

                event.deleteMessage();

                if(linked != null) {
                    sendDiscordMessage(linked, message);
                }
            }).schedule();
        });

        logger.info("Activity logging enabled for channel: #" + loggingChannel.toString().replaceAll(".*\\[|].*", "") + " (id: " + loggingChannelId + ")");
    }

    private String getPlayerLogName(Player player) {
        Long discordId = ProxyDiscord.inst().getLinkingManager().getLinked(player);
        Optional<ServerConnection> server = player.getCurrentServer();
        String serverName = server.isPresent() ? server.get().getServerInfo().getName() : "";

        if(discordId != null) {
            return "[" + serverName + "][" + player.getUsername() + "](<@!" + discordId + ">)";
        } else {
            return "[" + serverName + "][" + player.getUsername() + "](Unlinked)";
        }
    }

    private String getPlayerLogName(User user) {
         Long discordId = ProxyDiscord.inst().getLinkingManager().getLinked(user.getUniqueId());

        if(discordId != null) {
            return "[" + user.getFriendlyName() + "](<@!" + discordId + ">)";
        } else {
            return "[" + user.getFriendlyName() + "](Unlinked)";
        }
    }

    private void sendDiscordMessage(UUID uuid, String message) {
        try {
            LuckPerms luckPermsApi = LuckPermsProvider.get();

            UserManager userManager = luckPermsApi.getUserManager();
            User user = userManager.loadUser(uuid).join();

            if(user == null) {
                return;
            }

            if(message.isEmpty()) {
                return;
            }

            CachedMetaData metaData = user.getCachedData().getMetaData(QueryOptions.nonContextual());
            String prefix = ( metaData.getPrefix() != null) ?  metaData.getPrefix() : "";
            String suffix = ( metaData.getSuffix() != null) ?  metaData.getSuffix() : "";

            TextComponent.Builder component = Component.text();

            component.append(Component.text("DISCORD> ")
                                     .color(NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true));

            component.append(LegacyComponentSerializer.legacyAmpersand()
                                     .deserialize("&r" + prefix + user.getFriendlyName() + suffix + "&r: "));

            component.append(Component.text(message));

            proxy.getAllPlayers().forEach(player -> player.sendMessage(Identity.nil(), component.build()));
            sendLogMessage("[DISCORD] []" + getPlayerLogName(user) + "\n" + message);
        } catch (IllegalStateException e) {
            logger.warn("Failed to send Discord message: " + e.getMessage());
        }
    }

    private void sendLogMessage(String message) {
        if(loggingChannelId == null) {
            return;
        }

        String dateFormat = "<dd-MM-yy HH:mm:ss> ";
        SimpleDateFormat format = new SimpleDateFormat(dateFormat);
        String date = format.format(new Date());
        Optional <TextChannel> loggingChannel = ProxyDiscord.inst().getDiscord().getApi().getTextChannelById(loggingChannelId);

        message = date + message;

        synchronized (lockDummy) {
            if(currentMessage.getStringBuilder().length() + message.length() > 1950) {

                if(loggingChannel.isPresent()) {
                    queuedToSend.incrementAndGet();
                    currentMessage.send(loggingChannel.get())
                            .thenAcceptAsync(result -> queuedToSend.decrementAndGet()).exceptionally(error -> {
                        logger.warn("Failed to send log message");
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
                        logger.warn("Failed to send log message");
                        queuedToSend.decrementAndGet();
                        return null;
                    });
                }

                currentMessage = new MessageBuilder();
                unsentLogs.set(0);
            }

            //Increase logsPerMessage if many messages are queued, to help stop the log falling behind
            if(queuedToSend.get() >= 5 && logsPerMessage.get() < 16) {
                logger.info("Increasing logsPerMessage due to high activity (" + queuedToSend.get() + " queued messages)");
                logsPerMessage.set(Math.min(logsPerMessage.get() * 2, 16));
            }
        }
    }
}
