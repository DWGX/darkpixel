package com.darkpixel.anticheat.detectors;
import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.CheatType;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
public class KillAuraDetector implements Detector {
    private final double maxAngleDeviation;
    private final double maxHitsPerSecond;
    private final AntiCheatHandler handler;
    public KillAuraDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxAngleDeviation = config.getDouble("detectors.killaura.max_angle_deviation", 45.0);
        this.maxHitsPerSecond = config.getDouble("detectors.killaura.max_hits_per_second", 10.0);
        this.handler = handler;
    }
    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        double angleDeviation = args[0];
        long lastHit = data.lastHitTime != 0 ? timestamp - data.lastHitTime : 0;
        double hitsPerSecond = lastHit > 0 ? 1000.0 / lastHit : 0;
        double adjustedMaxAngle = maxAngleDeviation;
        double adjustedMaxHits = maxHitsPerSecond;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                adjustedMaxHits += effect.getAmplifier() * 2; 
            }
        }
        if (angleDeviation > adjustedMaxAngle || hitsPerSecond > adjustedMaxHits) {
            handler.triggerAlert(player, CheatType.KILLAURA,
                    "Angle: " + String.format("%.2f", angleDeviation) + ", Hits/s: " + String.format("%.2f", hitsPerSecond));
        }
        data.lastHitTime = timestamp;
    }
}