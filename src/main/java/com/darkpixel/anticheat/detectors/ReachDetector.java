package com.darkpixel.anticheat.detectors;

import com.darkpixel.Global; // 确保导入 Global 类
import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ReachDetector implements Detector {
    private final double maxReachDistance;
    private final AntiCheatHandler handler;
    private final Global plugin;

    public ReachDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxReachDistance = config.getDouble("detectors.reach.max_reach_distance", 4.5);
        this.handler = handler;
        this.plugin = handler.getContext(); // 从 AntiCheatHandler 获取 Global 实例
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        if (data.lastHitTime == 0) return;

        //将检查逻辑移到主线程
        new BukkitRunnable() {
            @Override
            public void run() {
                Entity target = player.getNearbyEntities(10, 10, 10).stream()
                        .filter(e -> e.getLocation().distance(player.getLocation()) > 0)
                        .findFirst().orElse(null);
                if (target == null) return;

                double reachDistance = player.getLocation().distance(target.getLocation());
                if (reachDistance > maxReachDistance && reachDistance < 50) {
                    handler.triggerAlert(player, AntiCheatHandler.CheatType.REACH,
                            "Reach Distance: " + String.format("%.2f", reachDistance) + " blocks");
                }
            }
        }.runTask(plugin.getPlugin());
    }
}