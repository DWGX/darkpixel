package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.AntiCheatHandler.Detector;
import com.darkpixel.anticheat.AntiCheatHandler.PlayerCheatData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MovementDetector implements Detector {
    private final double baseSpeed;
    private final double sprintMultiplier;
    private final double creativeMultiplier;
    private final double flightSpeed;
    private final double potionSpeedFactor;
    private final AntiCheatHandler handler;
    private double dynamicThreshold = 1.0;

    public MovementDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.baseSpeed = config.getDouble("detectors.movement.base_speed", 5.0);
        this.sprintMultiplier = config.getDouble("detectors.movement.sprint_multiplier", 1.3);
        this.creativeMultiplier = config.getDouble("detectors.movement.creative_multiplier", 2.5);
        this.flightSpeed = config.getDouble("detectors.movement.flight_speed", 25.0);
        this.potionSpeedFactor = config.getDouble("detectors.movement.potion_speed_factor", 0.25);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double x, double y, double z) {
        if (data.lastLocation == null || player.getVehicle() != null) return;

        Location from = data.lastLocation;
        double distance = Math.sqrt(Math.pow(x - from.getX(), 2) + Math.pow(z - from.getZ(), 2));
        double speed = distance / Math.max((timestamp - data.lastMoveTime) / 1000.0, 0.001);
        double maxSpeed = calculateMaxSpeed(player);

        if (speed > maxSpeed * 1.5 * dynamicThreshold) {
            handler.triggerAlert(player, AntiCheatHandler.CheatType.INVALID_MOVEMENT,
                    "Speed: " + String.format("%.2f", speed) + " blocks/s (Max: " + String.format("%.2f", maxSpeed) + ")");
        }

        data.lastLocation = new Location(player.getWorld(), x, y, z);
        data.lastMoveTime = timestamp;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, float yaw, float pitch) {}

    @Override
    public void setDynamicThreshold(double threshold) {
        this.dynamicThreshold = threshold;
    }

    private double calculateMaxSpeed(Player player) {
        double maxSpeed = baseSpeed;
        GameMode mode = player.getGameMode();

        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            if (player.isFlying()) return flightSpeed;
            maxSpeed *= creativeMultiplier;
        }

        if (player.isSprinting()) maxSpeed *= sprintMultiplier;

        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                maxSpeed *= (1 + (effect.getAmplifier() + 1) * potionSpeedFactor);
            }
        }

        if (!player.isOnGround()) maxSpeed *= 2.0;

        return maxSpeed;
    }
}