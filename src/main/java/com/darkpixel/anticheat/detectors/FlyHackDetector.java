package com.darkpixel.anticheat.detectors;
import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.CheatType;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
public class FlyHackDetector implements Detector {
    private final long maxAirTime; 
    private final double maxVerticalSpeed; 
    private final AntiCheatHandler handler;
    public FlyHackDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxAirTime = config.getLong("detectors.fly_hack.max_air_time", 5000L);
        this.maxVerticalSpeed = config.getDouble("detectors.fly_hack.max_vertical_speed", 1.0);
        this.handler = handler;
    }
    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        double y = args[1];
        if (player.isFlying() || player.hasPermission("minecraft.command.fly")) return;
        double verticalSpeed = data.lastLocation != null ? y - data.lastLocation.getY() : 0.0;
        boolean onGround = player.isOnGround() || player.getLocation().getBlock().getRelative(0, -1, 0).getType().isSolid();
        if (onGround) {
            data.lastGroundTime = timestamp;
        } else if (verticalSpeed > maxVerticalSpeed || (timestamp - data.lastGroundTime > maxAirTime)) {
            handler.triggerAlert(player, CheatType.FLY_HACK,
                    "Air Time: " + (timestamp - data.lastGroundTime) + "ms, Vertical Speed: " + String.format("%.2f", verticalSpeed));
        }
        data.lastVerticalSpeed = verticalSpeed;
    }
}