package com.darkpixel.anticheat.detectors;

import com.darkpixel.Global;
import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Comparator;

public class ReachDetector implements Detector {
    private final double maxReachDistance;
    private final AntiCheatHandler handler;
    private final Global plugin;

    public ReachDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxReachDistance = config.getDouble("detectors.reach.max_reach_distance", 6.0);
        this.handler = handler;
        this.plugin = handler.getContext();
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        if (data.lastHitTime == 0 || (timestamp - data.lastHitTime) > 1000) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                Entity target = player.getNearbyEntities(10, 10, 10).stream()
                        .filter(e -> e.getLocation().distanceSquared(player.getLocation()) > 0)
                        .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                        .orElse(null);
                if (target == null) return;

                double reachDistance = player.getLocation().distance(target.getLocation());
                if (reachDistance > maxReachDistance && reachDistance < 15) {
                    handler.triggerAlert(player, AntiCheatHandler.CheatType.REACH,
                            "Reach Distance: " + String.format("%.2f", reachDistance) + " blocks");
                }
            }
        }.runTask(plugin.getPlugin());
    }
}