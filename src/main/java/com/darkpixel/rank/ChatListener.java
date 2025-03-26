package com.darkpixel.rank;

import com.darkpixel.utils.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.stream.Collectors;

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
        String prefix = groups.stream()
                .map(group -> rankManager.getGroups().getOrDefault(group, new RankGroup(group, "§f", "", "", "[" + group + "]")).getPrefix())
                .collect(Collectors.joining(" "));
        String rank = rankManager.getRank(player);
        String format = prefix + " §e[" + rank + "] " + player.getName() + "§r: " + event.getMessage();
        event.setFormat(format);
    }
}