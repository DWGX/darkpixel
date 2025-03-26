package com.darkpixel.rank;

import com.darkpixel.utils.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;

public class ChatListener implements Listener {
    private final PlayerData playerData;
    private final RankManager rankManager;

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
        format.append(player.getName()).append("§r: ");

        String message = event.getMessage();
        if ("rainbow".equals(rankManager.getChatColor(player))) {
            StringBuilder rainbowMessage = new StringBuilder();
            String[] colors = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};
            for (int i = 0; i < message.length(); i++) {
                rainbowMessage.append(colors[i % colors.length]).append(message.charAt(i));
            }
            message = rainbowMessage.toString();
        }

        event.setFormat(format.toString() + message);
    }
}