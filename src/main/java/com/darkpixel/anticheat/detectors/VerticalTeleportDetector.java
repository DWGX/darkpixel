package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.AntiCheatHandler.Detector;
import com.darkpixel.anticheat.AntiCheatHandler.PlayerCheatData;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class VerticalTeleportDetector implements Detector {
    private final double maxDeltaY;
    private final AntiCheatHandler handler;
    private double dynamicThreshold = 1.0;

    public VerticalTeleportDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxDeltaY = config.getDouble("detectors.vertical_teleport.max_delta_y", 5.0);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double x, double y, double z) {
        if (data.lastLocation == null || player.isFlying()) return;

        double deltaY = y - data.lastLocation.getY();
        if (deltaY > maxDeltaY * dynamicThreshold && deltaY > 0 && !player.getLocation().getBlock().isLiquid()) {
            handler.triggerAlert(player, AntiCheatHandler.CheatType.VERTICAL_TELEPORT,
                    "Vertical Delta: " + String.format("%.2f", deltaY));
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
}