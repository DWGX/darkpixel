package com.darkpixel.rank;

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
        rankManager.setRank(event.getPlayer(), rankManager.getRank(event.getPlayer()), rankManager.getScore(event.getPlayer()));
    }
}