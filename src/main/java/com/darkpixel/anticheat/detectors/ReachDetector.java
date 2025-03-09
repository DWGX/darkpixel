package com.darkpixel.anticheat.detectors;

import com.darkpixel.Global;
import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.AntiCheatHandler.Detector;
import com.darkpixel.anticheat.AntiCheatHandler.PlayerCheatData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Comparator;

public class ReachDetector implements Detector {
    private final double maxReachDistance;
    private final AntiCheatHandler handler;
    private final Global plugin;
    private double dynamicThreshold = 1.0;

    public ReachDetector(YamlConfiguration config, AntiCheatHandler handler) {
        // 默认最大距离调整为 4.5 块，更符合 Minecraft 的实际攻击范围
        this.maxReachDistance = config.getDouble("detectors.reach.max_reach_distance", 4.5);
        this.handler = handler;
        this.plugin = handler.getContext();
    }


    //傻逼去死吧
    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double x, double y, double z) {
//        // 仅在最近 1000ms 内有攻击记录时检查
//        if (data.lastHitTime == 0 || (timestamp - data.lastHitTime) > 1000) {
//            return;
//        }
//
//        // 使用传入的玩家位置，避免异步任务中位置数据不一致
//        Location playerLoc = new Location(player.getWorld(), x, y, z);
//        new BukkitRunnable() {
//            @Override
//            public void run() {
//                Entity target = findNearestTarget(player);
//                if (target == null) return;
//
//                // 从玩家的眼睛位置计算距离，更加真实
//                double reachDistance = player.getEyeLocation().distance(target.getLocation());
//                double adjustedMaxReach = player.getGameMode() == GameMode.CREATIVE ? maxReachDistance * 2 : maxReachDistance;
//
//                if (reachDistance > adjustedMaxReach * dynamicThreshold && reachDistance < 20) {
//                    handler.triggerAlert(player, AntiCheatHandler.CheatType.REACH,
//                            "Reach Distance: " + String.format("%.2f", reachDistance) + " blocks");
//                }
//            }
//        }.runTask(plugin.getPlugin());
    }

    /**
     * 筛选最近的可攻击目标实体，确保只检测活体实体并验证视线方向
     */
    private Entity findNearestTarget(Player player) {
        return player.getNearbyEntities(15, 15, 15).stream()
                .filter(entity -> entity instanceof LivingEntity && !(entity instanceof ArmorStand)) // 仅检测活体实体
                .filter(entity -> {
                    // 检查视线方向，确保目标在玩家的视野范围内
                    Vector from = player.getEyeLocation().toVector();
                    Vector to = entity.getLocation().add(0, entity.getHeight() / 2, 0).toVector(); // 目标中心
                    Vector direction = to.subtract(from).normalize();
                    float dot = (float) player.getEyeLocation().getDirection().dot(direction);
                    return dot > 0.5f; // 视线方向与目标方向夹角小于 60 度
                })
                .min(Comparator.comparingDouble(entity -> player.getEyeLocation().distanceSquared(entity.getLocation())))
                .orElse(null);
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, float yaw, float pitch) {
    }

    @Override
    public void setDynamicThreshold(double threshold) {
        this.dynamicThreshold = threshold;
    }
}