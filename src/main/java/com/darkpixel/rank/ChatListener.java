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

        //使用缓存的聊天格式避免重复计算
        String cachedFormat = cachedFormats.computeIfAbsent(uuid, k -> buildChatFormat(player));
        String message = applyChatColor(player, ChatColor.stripColor(event.getMessage())); // 清理输入防止注入
        event.setFormat(cachedFormat + message);
    }

    private String buildChatFormat(Player player) {
        PlayerData.PlayerInfo info = playerData.getPlayerInfo(player.getName());
        List<String> groups = rankManager.getPlayerGroups(player);
        String groupPrefix = groups.stream()
                .map(group -> rankManager.getGroups().getOrDefault(group, new RankGroup(group, "§f", "", "", "[" + group + "]")).getPrefix())
                .findFirst().orElse("");
        String rank = rankManager.getRank(player);
        String[] rankParts = rank.split(" ");
        String baseRank = rankParts[0];
        String vip = rankParts.length > 1 ? rankParts[1] : "";
        StringBuilder format = new StringBuilder();

        if (rankManager.isShowGroup(player)) {
            format.append(groupPrefix).append(" ");
        }
        if (rankManager.isShowRank(player)) {
            format.append("§e★").append(baseRank);
            if (rankManager.isShowVip(player) && !vip.isEmpty()) {
                format.append(" §b").append(vip);
            }
            format.append("§r ");
        }
        format.append(player.getDisplayName()).append("§r: ");
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

    //更新缓（例如玩家权限或组变更时）
    public void updateCache(Player player) {
        cachedFormats.put(player.getUniqueId(), buildChatFormat(player));
    }
}