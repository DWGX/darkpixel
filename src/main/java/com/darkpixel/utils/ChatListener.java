package com.darkpixel.utils;

import com.darkpixel.rank.RankGroup;
import com.darkpixel.rank.RankManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;

public class ChatListener implements Listener {
    private final RankManager rankManager;

    public ChatListener(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        String playerName = event.getPlayer().getName();
        List<String> groups = rankManager.getPlayerGroups(event.getPlayer());
        String prefix = "";
        for (String group : groups) {
            RankGroup rankGroup = rankManager.getGroups().get(group);
            if (rankGroup != null) {
                prefix += rankGroup.getColor() + rankGroup.getEmoji() + rankGroup.getBadge() + " ";
            }
        }
        event.setFormat(prefix + ChatColor.WHITE + playerName + ": " + message);
    }
}