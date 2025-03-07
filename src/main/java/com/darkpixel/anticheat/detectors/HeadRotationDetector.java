package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

public class HeadRotationDetector implements Detector {
    private final double maxSpeed;
    private final AntiCheatHandler handler;

    public HeadRotationDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxSpeed = config.getDouble("detectors.head_rotation.max_speed", 150.0); // 从50.0提高到150.0
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        float yaw = (float) args[0];
        float pitch = (float) args[1];
        if (data.lastYaw != null && data.lastPitch != null && data.lastPacketTime > 0) {
            double deltaYaw = Math.abs(normalizeAngle(yaw - data.lastYaw));
            double deltaPitch = Math.abs(normalizeAngle(pitch - data.lastPitch));
            double deltaTime = (timestamp - data.lastPacketTime) / 1000.0;

            //避免除以接近0的时间差
            if (deltaTime < 0.01) return;

            double yawSpeed = deltaYaw / deltaTime;
            double pitchSpeed = deltaPitch / deltaTime;

            //添加调试日志
            Bukkit.getLogger().info(String.format("[DarkAC Debug] HeadRotation Check - Player: %s, YawSpeed: %.2f, PitchSpeed: %.2f, MaxSpeed: %.2f",
                    player.getName(), yawSpeed, pitchSpeed, maxSpeed));

            if (yawSpeed > maxSpeed || pitchSpeed > maxSpeed) {
                handler.triggerAlert(player, AntiCheatHandler.CheatType.FAST_HEAD_ROTATION,
                        "Yaw Speed: " + String.format("%.2f", yawSpeed) + ", Pitch Speed: " + String.format("%.2f", pitchSpeed));
            }
        }
        data.lastYaw = yaw;
        data.lastPitch = pitch;
        data.lastPacketTime = timestamp;
    }

    private float normalizeAngle(float angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
}