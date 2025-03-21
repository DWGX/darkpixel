package com.darkpixel.utils.effects;

import com.darkpixel.rank.RankManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class PlayerJoinEffects implements Listener {
    private final RankManager rankManager;

    public PlayerJoinEffects(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!rankManager.isEffectsEnabled(player)) return;
        List<String> groups = rankManager.getPlayerGroups(player);
        for (String group : groups) {
            player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 100);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }
}