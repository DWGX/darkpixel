package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
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
        this.maxAngleDeviation = config.getDouble("detectors.killaura.max_angle_deviation", 90.0);
        this.maxHitsPerSecond = config.getDouble("detectors.killaura.max_hits_per_second", 20.0);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        if (data.lastHitTime == 0) return;

        double angleDeviation = player.getEyeLocation().getDirection().angle(player.getLocation().getDirection());
        long lastHit = timestamp - data.lastHitTime;
        double hitsPerSecond = lastHit > 0 ? 1000.0 / lastHit : 0;

        double adjustedMaxHits = maxHitsPerSecond;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                adjustedMaxHits += effect.getAmplifier() * 1.5;
            }
        }

        if (angleDeviation > maxAngleDeviation && hitsPerSecond > 0 && hitsPerSecond <= adjustedMaxHits) {
            handler.triggerAlert(player, AntiCheatHandler.CheatType.KILLAURA,
                    "Angle: " + String.format("%.2f", angleDeviation * 180 / Math.PI) + ", Hits/s: " + String.format("%.2f", hitsPerSecond));
        }
        data.lastHitTime = timestamp;
    }
}