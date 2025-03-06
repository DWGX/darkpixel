package com.darkpixel.anticheat.detectors;
import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.CheatType;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
public class VerticalTeleportDetector implements Detector {
    private final double maxDeltaY;
    private final AntiCheatHandler handler;
    public VerticalTeleportDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxDeltaY = config.getDouble("detectors.vertical_teleport.max_delta_y", 1.5);
        this.handler = handler;
    }
    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        double y = args[1];
        if (data.lastLocation != null && !player.isFlying() && !player.getLocation().getBlock().isLiquid()) {
            double deltaY = Math.abs(y - data.lastLocation.getY());
            if (deltaY > maxDeltaY) {
                handler.triggerAlert(player, CheatType.VERTICAL_TELEPORT,
                        "Vertical Delta: " + String.format("%.2f", deltaY));
            }
        }
        data.lastLocation = new Location(player.getWorld(), args[0], y, args[2]);
        data.lastMoveTime = timestamp;
    }
}