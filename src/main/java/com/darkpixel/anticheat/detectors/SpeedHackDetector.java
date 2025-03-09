package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.AntiCheatHandler.Detector;
import com.darkpixel.anticheat.AntiCheatHandler.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SpeedHackDetector implements Detector {
    private final double maxSpeed;
    private final AntiCheatHandler handler;
    private double dynamicThreshold = 1.0;

    public SpeedHackDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxSpeed = config.getDouble("detectors.speed_hack.max_speed", 20.0);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double x, double y, double z) {
        if (data.lastLocation == null || player.isFlying()) return;

        double distance = Math.sqrt(Math.pow(x - data.lastLocation.getX(), 2) + Math.pow(z - data.lastLocation.getZ(), 2));
        double speed = distance / (timestamp - data.lastMoveTime) / 1000.0;
        double adjustedMaxSpeed = maxSpeed;

        if (player.isSprinting()) adjustedMaxSpeed *= 1.3;

        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                adjustedMaxSpeed *= (1 + (effect.getAmplifier() + 1) * 0.2);
            }
        }

        if (speed > adjustedMaxSpeed * 1.5 * dynamicThreshold && player.isOnGround()) {
            handler.triggerAlert(player, AntiCheatHandler.CheatType.SPEED_HACK,
                    "Speed: " + String.format("%.2f", speed) + " blocks/s");
        }
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, float yaw, float pitch) {}

    @Override
    public void setDynamicThreshold(double threshold) {
        this.dynamicThreshold = threshold;
    }
}