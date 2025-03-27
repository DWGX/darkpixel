package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.CheckPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Map;
import java.util.UUID;

public class HeadRotationDetector extends AbstractCheckDetector {
    private final double maxAngle;
    private final long minTimeMs;

    public HeadRotationDetector(YamlConfiguration config, AntiCheatHandler handler, Map<UUID, CheckPlayer> checkPlayers) {
        super(config, handler, checkPlayers);
        this.maxAngle = config.getDouble("detectors.head_rotation.max_angle", 90.0);
        this.minTimeMs = config.getLong("detectors.head_rotation.min_time_ms", 50);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType().equals(PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION)) {
            WrapperPlayClientPlayerPositionAndRotation posRot = new WrapperPlayClientPlayerPositionAndRotation(event);
            CheckPlayer checkPlayer = checkPlayers.get(((org.bukkit.entity.Player) event.getPlayer()).getUniqueId());

            if (checkPlayer == null) return;

            float currentYaw = posRot.getYaw();
            float currentPitch = posRot.getPitch();
            float previousYaw = checkPlayer.getYaw();
            float previousPitch = checkPlayer.getPitch();

            long now = System.currentTimeMillis();
            long lastPacketTime = checkPlayer.getPreviousPacketEvent() != null ? checkPlayer.getPreviousPacketEvent().getTimestamp() : now;
            long deltaTimeMs = now - lastPacketTime;

            if (deltaTimeMs < 1) deltaTimeMs = 1; // 避免除零

            if (deltaTimeMs < minTimeMs) {
                float deltaYaw = Math.abs(normalizeAngle(currentYaw - previousYaw));
                float deltaPitch = Math.abs(normalizeAngle(currentPitch - previousPitch));
                if (deltaYaw > maxAngle || deltaPitch > maxAngle) {
                    float speed = Math.max(deltaYaw, deltaPitch) / (deltaTimeMs / 1000.0f);
                    handler.triggerAlert(checkPlayer.getPlayer(), AntiCheatHandler.CheatType.FAST_HEAD_ROTATION,
                            String.format("Yaw: %.1f°, Pitch: %.1f°, Speed: %.1f°/s", deltaYaw, deltaPitch, speed));
                }
            }
        }
    }

    private float normalizeAngle(float angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
}