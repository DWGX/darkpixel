package com.darkpixel.anticheat.detectors;
import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.CheatType;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
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
        double reachDistance = args[0];
        if (reachDistance > maxReachDistance) {
            handler.triggerAlert(player, CheatType.REACH,
                    "Reach Distance: " + String.format("%.2f", reachDistance) + " blocks");
        }
    }
}