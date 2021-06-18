package uk.co.notnull.proxydiscord.manager;

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
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import ninja.leaping.configurate.ConfigurationNode;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.mention.AllowedMentions;
import org.javacord.api.entity.message.mention.AllowedMentionsBuilder;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;
import org.slf4j.Logger;
import uk.co.notnull.proxydiscord.ProxyDiscord;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggingManager {
    private final ProxyDiscord plugin;
    private final LinkingManager linkingManager;
    private String loggingChannelId = null;
    private final Integer lockDummy = 0;

    private final AtomicInteger logsPerMessage = new AtomicInteger(1); //Number of logs to combine together into one message, to avoid rate limits and falling behind
    private final AtomicInteger unsentLogs = new AtomicInteger(0); //Number of unsent logs in current message
    private final AtomicInteger queuedToSend = new AtomicInteger(0); //Number of messages waiting to be sent by javacord

    private boolean deleteSentMessages;
    private SimpleDateFormat dateFormat;
    private String chatFormat;
    private String discordChatFormat;
    private String joinFormat;
    private String leaveFormat;
    private String commandFormat;

    private final AllowedMentions allowedMentions;
    private MessageBuilder currentMessage; //Current unsent message
    private ListenerManager<MessageCreateListener> logListener = null;

    private final ProxyServer proxy;
    private final Logger logger;

    public LoggingManager(ProxyDiscord plugin, ConfigurationNode config) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();
        this.linkingManager = plugin.getLinkingManager();

        currentMessage = new MessageBuilder();
        proxy.getEventManager().register(plugin, this);

        plugin.getDiscord().getApi().addReconnectListener(event -> {
            if(loggingChannelId != null) {
                findChannel();
            }
        });

        AllowedMentionsBuilder allowedMentionsBuilder = new AllowedMentionsBuilder();
        allowedMentionsBuilder.setMentionRoles(false).setMentionUsers(false).setMentionEveryoneAndHere(false);
        allowedMentions = allowedMentionsBuilder.build();

        //Decrease logs per message if a low number of messages are unsent
        proxy.getScheduler().buildTask(plugin, () -> {
            if(queuedToSend.get() <= 2 && logsPerMessage.get() > 1) {
                logger.info("Decreasing logsPerMessage due to low activity (" + queuedToSend.get() + " queued messages)");
                logsPerMessage.set(Math.max(logsPerMessage.get() / 2, 1));
            }
        }).repeat( 5, TimeUnit.SECONDS).delay( 5, TimeUnit.SECONDS).schedule();

        parseConfig(config);
    }

    public void parseConfig(ConfigurationNode config) {
        loggingChannelId = config.getNode("logging-channel-id").getString();
        deleteSentMessages = config.getNode("logging-delete-sent-messages").getBoolean(false);

        ConfigurationNode logFormats = config.getNode("logging-formats");

        if(logFormats.isMap()) {
            ConfigurationNode dateFormat = logFormats.getNode("date");
            ConfigurationNode chatFormat = logFormats.getNode("chat");
            ConfigurationNode discordChatFormat = logFormats.getNode("discord-chat");
            ConfigurationNode joinFormat = logFormats.getNode("join");
            ConfigurationNode leaveFormat = logFormats.getNode("leave");
            ConfigurationNode commandFormat = logFormats.getNode("command");

            try {
                this.dateFormat = !dateFormat.isEmpty() ? new SimpleDateFormat(dateFormat.getString("")) : null;
            } catch(IllegalArgumentException e) {
                logger.warn("Invalid logging date format: " + e.getMessage());
            }

            this.chatFormat = !chatFormat.isEmpty() ? chatFormat.getString(null) : null;
            this.discordChatFormat = !discordChatFormat.isEmpty() ? discordChatFormat.getString(null) : null;
            this.joinFormat = !joinFormat.isEmpty() ? joinFormat.getString(null) : null;
            this.leaveFormat = !leaveFormat.isEmpty() ? leaveFormat.getString(null) : null;
            this.commandFormat = !commandFormat.isEmpty() ? commandFormat.getString(null) : null;
        }

        if(loggingChannelId != null) {
            findChannel();
        }
    }

    public void logJoin(Player player) {
        if(joinFormat != null) {
            sendLogMessage(player, joinFormat);
        }
    }

    public void logLeave(Player player) {
        if(leaveFormat != null) {
            sendLogMessage(player, leaveFormat);
        }
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPlayerChat(PlayerChatEvent e) {
        if(!e.getResult().isAllowed() || chatFormat == null) {
            return;
        }

        Player sender = e.getPlayer();
        String message = e.getMessage().replace("```", "");

        sendLogMessage(sender, chatFormat.replaceAll("\\[message]", message));
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPlayerCommand(CommandExecuteEvent e) {
        if(!e.getResult().isAllowed() || commandFormat == null) {
            return;
        }

        if(!(e.getCommandSource() instanceof Player)) {
            return;
        }

        Player sender = (Player) e.getCommandSource();
        String command = e.getCommand().replace("```", "");

        sendLogMessage(sender, commandFormat.replaceAll("\\[command]", command));
    }

    private void findChannel() {
        if(loggingChannelId == null) {
            return;
        }

        Optional <TextChannel> loggingChannel = plugin.getDiscord().getApi().getTextChannelById(loggingChannelId);

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

            proxy.getScheduler().buildTask(plugin, () -> {
                String message = event.getReadableMessageContent();
                Long discordId = event.getMessage().getAuthor().getId();
                UUID linked = linkingManager.getLinked(discordId);

                if(deleteSentMessages) {
                    event.deleteMessage();
                }

                if(linked != null) {
                    sendDiscordMessage(linked, message);
                }
            }).schedule();
        });

        logger.info("Activity logging enabled for channel: #" + loggingChannel.toString().replaceAll(".*\\[|].*", "") + " (id: " + loggingChannelId + ")");
    }

    private void sendDiscordMessage(UUID uuid, String message) {
        try {
            UserManager userManager = plugin.getLuckpermsManager().getUserManager();
            User user = userManager.loadUser(uuid).join();

            if(user == null || message.isEmpty() || discordChatFormat == null) {
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
            sendLogMessage(user, discordChatFormat.replaceAll("\\[message]", message));
        } catch (IllegalStateException e) {
            logger.warn("Failed to send Discord message: " + e.getMessage());
        }
    }

    private void sendLogMessage(Player player, String message) {
        Long discordId = linkingManager.getLinked(player);
        Optional<ServerConnection> server = player.getCurrentServer();
        String serverName = server.isPresent() ? server.get().getServerInfo().getName() : "";

        message = message.replaceAll("\\[server]", serverName);
        message = message.replaceAll("\\[player]", player.getUsername());
        message = message.replaceAll("\\[discord_id]", discordId != null ? String.valueOf(discordId) : "Unlinked");

        sendLogMessage(message);
    }

    private void sendLogMessage(User user, String message) {
        Long discordId = linkingManager.getLinked(user.getUniqueId());

        message = message.replaceAll("\\[server]", "");
        message = message.replaceAll("\\[player]", user.getFriendlyName());
        message = message.replaceAll("\\[discord_id]", discordId != null ? String.valueOf(discordId) : "Unlinked");

        sendLogMessage(message);
    }

    private void sendLogMessage(String message) {
        if(loggingChannelId == null) {
            return;
        }

        message = message.replaceAll("\\[date]", dateFormat != null ? dateFormat.format(new Date()) : "");

        Optional <TextChannel> loggingChannel = plugin.getDiscord().getApi().getTextChannelById(loggingChannelId);

        synchronized (lockDummy) {
            if(currentMessage.getStringBuilder().length() + message.length() > 1950) {

                if(loggingChannel.isPresent()) {
                    queuedToSend.incrementAndGet();
                    currentMessage.setAllowedMentions(allowedMentions);
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

            currentMessage.append(message);

            if(unsentLogs.incrementAndGet() >= logsPerMessage.get()) {

                if(loggingChannel.isPresent()) {
                    queuedToSend.incrementAndGet();
                    currentMessage.setAllowedMentions(allowedMentions);
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

            //Increase logsPerMessage if many messages are queued, to help stop the log falling behind
            if(queuedToSend.get() >= 5 && logsPerMessage.get() < 16) {
                logger.info("Increasing logsPerMessage due to high activity (" + queuedToSend.get() + " queued messages)");
                logsPerMessage.set(Math.min(logsPerMessage.get() * 2, 16));
            }
        }
    }
}
