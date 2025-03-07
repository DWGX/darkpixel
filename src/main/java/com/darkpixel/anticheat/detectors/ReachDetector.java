package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class ReachDetector implements Detector {
    private final double maxReachDistance;
    private final AntiCheatHandler handler;

    public ReachDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxReachDistance = config.getDouble("detectors.reach.max_reach_distance", 4.5);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        if (data.lastHitTime == 0) return;

        Entity target = player.getNearbyEntities(10, 10, 10).stream()
                .filter(e -> e.getLocation().distance(player.getLocation()) > 0)
                .findFirst().orElse(null);
        if (target == null) return;

        double reachDistance = player.getLocation().distance(target.getLocation());
        if (reachDistance > maxReachDistance && reachDistance < 50) { // 添加上限避免异常值
            handler.triggerAlert(player, AntiCheatHandler.CheatType.REACH,
                    "Reach Distance: " + String.format("%.2f", reachDistance) + " blocks");
        }
    }
}