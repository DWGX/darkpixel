package com.darkpixel.rank;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class RankEvents implements Listener {
    private final RankManager rankManager;

    public RankEvents(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!rankManager.getAllRanks().containsKey(player.getUniqueId())) {
            rankManager.setRank(player, "member", 0); // 使用修复后的方法
        }
    }
}