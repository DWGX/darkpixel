package com.darkpixel.rank;

import com.darkpixel.utils.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.Random;

public class ChatListener implements Listener {
    private final PlayerData playerData;
    private final RankManager rankManager;
    private final Random random = new Random();
    private final String[] randomColors = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};

    public ChatListener(PlayerData playerData, RankManager rankManager) {
        this.playerData = playerData;
        this.rankManager = rankManager;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
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

        String message = event.getMessage();
        String chatColor = rankManager.getChatColor(player);

        switch (chatColor) {
            case "rainbow":
                StringBuilder rainbowMessage = new StringBuilder();
                String[] colors = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};
                for (int i = 0; i < message.length(); i++) {
                    rainbowMessage.append(colors[i % colors.length]).append(message.charAt(i));
                }
                message = rainbowMessage.toString();
                break;
            case "random":
                StringBuilder randomMessage = new StringBuilder();
                for (int i = 0; i < message.length(); i++) {
                    randomMessage.append(randomColors[random.nextInt(randomColors.length)]).append(message.charAt(i));
                }
                message = randomMessage.toString();
                break;
            case "gradient":
                StringBuilder gradientMessage = new StringBuilder();
                int length = message.length();
                for (int i = 0; i < length; i++) {
                    int colorIndex = (int) ((float) i / length * (randomColors.length - 1));
                    gradientMessage.append(randomColors[colorIndex]).append(message.charAt(i));
                }
                message = gradientMessage.toString();
                break;
            case "normal":
                break;
            default:
                message = chatColor + message;
                break;
        }

        event.setFormat(format.toString() + message);
    }
}