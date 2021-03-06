package io.github.icrazyblaze.twitchmod.irc;


import com.google.common.collect.ImmutableMap;
import io.github.icrazyblaze.twitchmod.CommandHandlers;
import io.github.icrazyblaze.twitchmod.Main;
import io.github.icrazyblaze.twitchmod.chat.ChatPicker;
import io.github.icrazyblaze.twitchmod.util.BotConfig;
import io.github.icrazyblaze.twitchmod.util.CalculateMinecraftColor;
import net.minecraft.util.concurrent.ThreadTaskExecutor;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.LogicalSidedProvider;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PingEvent;

import java.awt.*;
import java.util.Objects;


public class TwitchBot extends ListenerAdapter {

    public TwitchBot() {
        ChatPicker.loadBlacklistFile();
    }

    public void onConnect(ConnectEvent event) {
        CommandHandlers.broadcastMessage(new StringTextComponent(TextFormatting.DARK_GREEN + "Bot connected! Use /ttv status to see details."));
        Main.logger.info("IRC Bot connected.");
    }

    public void onDisconnect(DisconnectEvent event) {
        CommandHandlers.broadcastMessage(new StringTextComponent(TextFormatting.DARK_RED + "Bot disconnected."));
        Main.logger.info("IRC Bot disconnected: " + event.getDisconnectException());
    }

    @Override
    public void onMessage(MessageEvent event) {

        String message = event.getMessage();
        String sender = Objects.requireNonNull(event.getUser()).getNick();
        ImmutableMap<String, String> tags = event.getV3Tags();

        TextFormatting format = TextFormatting.WHITE;

        StringTextComponent showText;
        String role = null;

        if (BotConfig.showChatMessages) {

            ChatPicker.forceCommands = false;

            if (tags != null) {

                // Get hex colour, convert to RGB, then get nearest Minecraft colour code
                Color userColor = Color.decode(tags.get("color"));
				
				try {
					if (format != null)
						format = CalculateMinecraftColor.findNearestMinecraftColor(userColor);
				}
				catch(Exception e) {
					Main.logger.info("No valid user colour");
				}

                if (tags.get("badges").contains("broadcaster/1")) {
                    ChatPicker.forceCommands = true; // Force commands to execute instantly for broadcaster testing
                    role = "Broadcaster";
                } else if (tags.get("badges").contains("subscriber/1")) {
                    role = "Subscriber";
                } else if (tags.get("badges").contains("moderator/1")) {
                    role = "Moderator";
                }

            }

            if (!message.startsWith(BotConfig.prefix) || BotConfig.showCommands) {

                showText = new StringTextComponent(String.format("%s<%sTwitch %s%s%s> %s", TextFormatting.WHITE, TextFormatting.DARK_PURPLE, format, sender, TextFormatting.WHITE, message));

                if (role != null) {
                    showText.setStyle(showText.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent(format + role))));
                }

                CommandHandlers.broadcastMessage(showText);

            }

        }

        if (message.equalsIgnoreCase(BotConfig.prefix + "help") || message.equalsIgnoreCase(BotConfig.prefix + "commands")) {

            event.respond("Click here for a list of commands: http://bit.ly/2UfBCiL");

        } else if (message.equalsIgnoreCase(BotConfig.prefix + "modlink")) {

            event.respond("Click here to download the mod: http://bit.ly/TwitchVsMinecraft");

        } else if (message.startsWith(BotConfig.prefix + "blacklist")) {

            message = message.substring(BotConfig.prefix.length());

            // Moved adding to and clearing blacklist to the Twitch chat (only for mods and broadcasters)
            if (message.startsWith("blacklist ")) {

                if (role.equals("Moderator") || role.equals("Broadcaster")) {

                    if (message.substring(10).startsWith("add ")) {
                        ChatPicker.addToBlacklist(message.substring(14));
                    } else if (message.substring(10).equalsIgnoreCase("clear")) {
                        ChatPicker.clearBlacklist();
                        event.respond("Blacklist cleared.");
                    }

                }

            }
            ChatPicker.loadBlacklistFile();
            event.respond("Blacklisted commands: " + ChatPicker.blacklist.toString());

        } else if (message.equalsIgnoreCase(BotConfig.prefix + "disconnect")) {
            TwitchConnectionHelper.disconnectBot();

        } else if (message.equalsIgnoreCase(BotConfig.prefix + "reconnect")) {

            TwitchConnectionHelper.tryConnect();

        } else if (message.startsWith(BotConfig.prefix) || ChatPicker.tempLogMessages) {

            String finalMessage = message;

            Runnable runnable = (() -> {

                // Add command to queue
                ChatPicker.checkChat(finalMessage, sender);

            });

            // Only run on main thread
            ThreadTaskExecutor executor = LogicalSidedProvider.WORKQUEUE.get(LogicalSide.SERVER);
            if (!executor.isOnExecutionThread()) {
                executor.deferTask(runnable);
            } else {
                runnable.run();
            }

        }

    }

    // Prevent the bot from being kicked
    @Override
    public void onPing(PingEvent event) throws Exception {
        TwitchConnectionHelper.bot.sendRaw().rawLineNow(String.format("PONG %s\r\n", event.getPingValue()));
    }

}