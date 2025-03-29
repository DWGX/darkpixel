package com.darkpixel.utils;

import com.darkpixel.Global;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class MessageHandler {
    private final Global context;

    public MessageHandler(Global context) {
        this.context = context;
    }

    public void sendJoinMessage(Player player, String rank, int loginCount) {
        String playerName = player.getName();
        String displayName = player.getDisplayName() != null ? player.getDisplayName() : playerName;

        String joinMessage = "§7大厅 §8| §a" + playerName + " (" + rank + ") 欢迎第 " + loginCount + " 次加入黑像素服务器";
        Bukkit.broadcastMessage(joinMessage);

        String tellrawMessage = "[{\"text\":\"§l§6欢迎 \",\"bold\":true},{\"text\":\"" + displayName + "\",\"color\":\"aqua\",\"bold\":true},{\"text\":\" §e加入服务器！\",\"bold\":true},{\"text\":\" (§b第 " + loginCount + " 次§e)\",\"color\":\"yellow\"}]";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw @a " + tellrawMessage);
    }

    public void sendAiWelcomeMessage(Player player, String rank, int score, int loginCount) {
        if (!player.isOnline()) return;

        String playerName = player.getName();
        String message = "玩家 " + playerName + "（Rank: " + rank + "，分数: " + score + "）第 " + loginCount + " 次加入服务器，坐标: " +
                player.getLocation().getBlockX() + "," + player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ() + "，请用中文生成一个自然、友好的欢迎消息";

        context.getAiChat().sendMessage(player, message, false, response -> {
            if (player.isOnline()) {
                player.sendMessage("§b" + response);
            }
        });
    }
}