package com.darkpixel.rank;

import com.darkpixel.Global;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatListener implements Listener {
    private final Global context;
    private final Map<UUID, String> cachedFormats;

    public ChatListener(Global context) {
        this.context = context;
        this.cachedFormats = new HashMap<>();
    }

    public String getChatColor(Player player) {
        String color = context.getRankManager().getAllRanks()
                .getOrDefault(player.getUniqueId(), new RankData("member", 0))
                .getChatColor();
        Bukkit.getLogger().info("Player " + player.getName() + " chat color: " + color);
        return color;
    }

    public String applyChatColor(Player player, String message) {
        String chatColor = getChatColor(player);

        if ("normal".equalsIgnoreCase(chatColor) || chatColor == null) {
            return ChatColor.RESET + message;
        } else if ("random".equalsIgnoreCase(chatColor)) {
            StringBuilder coloredMessage = new StringBuilder();
            ChatColor[] colors = {ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW, ChatColor.LIGHT_PURPLE};
            for (int i = 0; i < message.length(); i++) {
                coloredMessage.append(colors[i % colors.length]).append(message.charAt(i));
            }
            return coloredMessage.toString();
        }

        try {
            return ChatColor.valueOf(chatColor.toUpperCase()) + message;
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().warning("Invalid chat color '" + chatColor + "' for player " + player.getName() + ", defaulting to white.");
            return ChatColor.WHITE + message;
        }
    }

    public String buildChatFormat(Player player) {
        RankData data = context.getRankManager().getAllRanks()
                .getOrDefault(player.getUniqueId(), new RankData("member", 0));
        List<String> groups = context.getRankManager().getPlayerGroups(player);
        String groupPrefix = groups.isEmpty() ? "[Member]" : context.getRankManager().getGroups().get(groups.get(0)).getPrefix();
        StringBuilder format = new StringBuilder();
        int stars = data.getScore() / 1000;
        String starString = stars > 0 ? ChatColor.GOLD + "★".repeat(Math.min(stars, 5)) : "";

        for (String part : data.getDisplayOrder()) {
            switch (part) {
                case "score":
                    if (data.isShowScore()) {
                        format.append(ChatColor.GRAY).append("[").append(data.getScore()).append("]");
                    }
                    break;
                case "group":
                    if (data.isShowGroup()) {
                        format.append(ChatColor.YELLOW).append("[").append(groupPrefix).append("]");
                    }
                    break;
                case "rank":
                    if (data.isShowRank()) {
                        format.append(ChatColor.AQUA).append("[").append(data.getRank()).append("]");
                    }
                    break;
            }
        }
        format.append(starString).append(ChatColor.WHITE).append(" ").append(player.getName()).append(": ");
        return format.toString();
    }

    public void updateCache(Player player) {
        String format = buildChatFormat(player);
        Bukkit.getLogger().info("Updating chat format for " + player.getName() + ": " + format);
        cachedFormats.put(player.getUniqueId(), format);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String cachedFormat = cachedFormats.computeIfAbsent(uuid, k -> buildChatFormat(player));
        String coloredMessage = applyChatColor(player, ChatColor.stripColor(event.getMessage()));
        String finalFormat = cachedFormat + coloredMessage;
        Bukkit.getLogger().info("Chat format for " + player.getName() + ": " + finalFormat);
        event.setFormat(finalFormat);
    }
}