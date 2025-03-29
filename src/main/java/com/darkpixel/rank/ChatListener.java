package com.darkpixel.rank;

import com.darkpixel.utils.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatListener implements Listener {
    private final PlayerData playerData;
    private final RankManager rankManager;
    private final Random random = new Random();
    private final String[] randomColors = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};
    private final ConcurrentHashMap<UUID, String> cachedFormats = new ConcurrentHashMap<>();

    public ChatListener(PlayerData playerData, RankManager rankManager) {
        this.playerData = playerData;
        this.rankManager = rankManager;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String cachedFormat = cachedFormats.computeIfAbsent(uuid, k -> buildChatFormat(player));
        String message = applyChatColor(player, ChatColor.stripColor(event.getMessage()));
        event.setFormat(cachedFormat + message);
    }

    private String buildChatFormat(Player player) {
        UUID uuid = player.getUniqueId();
        RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
        List<String> groups = rankManager.getPlayerGroups(player);

        String groupPrefix = groups.isEmpty() ? "[Member]" : rankManager.getGroups().get(groups.get(0)).getPrefix();

        StringBuilder format = new StringBuilder();
        String baseColor = data.getChatColor().equals("normal") ? "§f" : data.getChatColor();
        format.append(baseColor);

        if (data.isShowScore()) format.append("[").append(data.getScore()).append("]");
        if (data.isShowGroup()) format.append(groupPrefix);
        if (data.isShowRank()) format.append("[").append(data.getRank()).append("]");
        if (data.isShowVip() && !data.getRank().equals("member")) format.append("[§b★").append(baseColor).append("]");

        format.append(player.getName()).append("§r: ");
        return format.toString();
    }

    private String applyChatColor(Player player, String message) {
        String chatColor = rankManager.getChatColor(player);
        switch (chatColor) {
            case "rainbow":
                StringBuilder rainbowMessage = new StringBuilder();
                String[] colors = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};
                for (int i = 0; i < message.length(); i++) {
                    rainbowMessage.append(colors[i % colors.length]).append(message.charAt(i));
                }
                return rainbowMessage.toString();
            case "random":
                StringBuilder randomMessage = new StringBuilder();
                for (int i = 0; i < message.length(); i++) {
                    randomMessage.append(randomColors[random.nextInt(randomColors.length)]).append(message.charAt(i));
                }
                return randomMessage.toString();
            case "gradient":
                StringBuilder gradientMessage = new StringBuilder();
                int length = message.length();
                for (int i = 0; i < length; i++) {
                    int colorIndex = (int) ((float) i / length * (randomColors.length - 1));
                    gradientMessage.append(randomColors[colorIndex]).append(message.charAt(i));
                }
                return gradientMessage.toString();
            case "normal":
                return message;
            default:
                return chatColor + message;
        }
    }

    public void updateCache(Player player) {
        cachedFormats.put(player.getUniqueId(), buildChatFormat(player));
    }
}