package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class MovementDetector implements Detector {
    private final double maxSpeed;
    private final AntiCheatHandler handler;

    public MovementDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxSpeed = config.getDouble("detectors.movement.max_speed", 0.6);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        double x = args[0];
        double y = args[1];
        double z = args[2];
        if (data.lastLocation != null && !player.isFlying()) {
            Location from = data.lastLocation;
            double deltaX = x - from.getX();
            double deltaZ = z - from.getZ();
            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            double deltaTime = (timestamp - data.lastMoveTime) / 1000.0;
            double speed = distance / deltaTime;
            if (speed > maxSpeed) {
                handler.triggerAlert(player, AntiCheatHandler.CheatType.INVALID_MOVEMENT,
                        "Speed: " + String.format("%.2f", speed) + " blocks/s");
            }
        }
        data.lastLocation = new Location(player.getWorld(), x, y, z);
        data.lastMoveTime = timestamp;
    }
}