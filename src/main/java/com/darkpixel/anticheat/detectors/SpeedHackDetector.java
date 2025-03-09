package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SpeedHackDetector implements Detector {
    private final double maxSpeed;
    private final AntiCheatHandler handler;

    public SpeedHackDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxSpeed = config.getDouble("detectors.speed_hack.max_speed", 15.0);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        double x = args[0];
        double z = args[2];
        if (data.lastLocation == null || player.isFlying()) return;
        double deltaX = x - data.lastLocation.getX();
        double deltaZ = z - data.lastLocation.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double deltaTime = (timestamp - data.lastMoveTime) / 1000.0;
        double speed = distance / deltaTime;
        double adjustedMaxSpeed = maxSpeed;
        if (player.isSprinting()) adjustedMaxSpeed *= 1.3;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                adjustedMaxSpeed *= (1 + (effect.getAmplifier() + 1) * 0.2);
            }
        }
        if (speed > adjustedMaxSpeed && player.isOnGround()) {
            handler.triggerAlert(player, AntiCheatHandler.CheatType.SPEED_HACK,
                    "Speed: " + String.format("%.2f", speed) + " blocks/s (Max: " + String.format("%.2f", adjustedMaxSpeed) + ")");
        }
    }
}