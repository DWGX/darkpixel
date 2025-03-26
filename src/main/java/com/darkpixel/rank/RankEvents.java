package com.darkpixel.rank;

import com.darkpixel.Global;
import com.darkpixel.utils.PlayerData;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class RankEvents implements Listener {
    private final RankManager rankManager;
    private final PlayerData playerData;

    public RankEvents(RankManager rankManager, PlayerData playerData) {
        this.rankManager = rankManager;
        this.playerData = playerData;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!rankManager.getAllRanks().containsKey(player.getUniqueId())) {
            rankManager.setRank(player, "member", 0, Particle.FIREWORK, "欢迎 {player} 加入服务器！");
        }
        Global.executor.submit(() -> playerData.updatePlayer(player));
    }
}