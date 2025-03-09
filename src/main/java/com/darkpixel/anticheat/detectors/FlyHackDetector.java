package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.AntiCheatHandler.Detector;
import com.darkpixel.anticheat.AntiCheatHandler.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class FlyHackDetector implements Detector {
    private final long maxAirTime;
    private final double maxVerticalSpeed;
    private final AntiCheatHandler handler;
    private double dynamicThreshold = 1.0;

    public FlyHackDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxAirTime = config.getLong("detectors.fly_hack.max_air_time", 8000);
        this.maxVerticalSpeed = config.getDouble("detectors.fly_hack.max_vertical_speed", 2.0);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double x, double y, double z) {
        if (player.isFlying() || player.hasPermission("minecraft.command.fly")) return;
        double verticalSpeed = data.lastLocation != null ? y - data.lastLocation.getY() : 0.0;
        boolean onGround = player.isOnGround();

        if (onGround) {
            data.lastGroundTime = timestamp;
        } else if (verticalSpeed > maxVerticalSpeed * dynamicThreshold || (timestamp - data.lastGroundTime > maxAirTime * dynamicThreshold)) {
            handler.triggerAlert(player, AntiCheatHandler.CheatType.FLY_HACK,
                    "Air Time: " + (timestamp - data.lastGroundTime) + "ms");
        }
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, float yaw, float pitch) {}

    @Override
    public void setDynamicThreshold(double threshold) {
        this.dynamicThreshold = threshold;
    }
}