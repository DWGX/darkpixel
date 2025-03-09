package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.AntiCheatHandler.Detector;
import com.darkpixel.anticheat.AntiCheatHandler.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class BlinkDetector implements Detector {
    private final long maxInterval;
    private final int triggerCount;
    private final AntiCheatHandler handler;
    private double dynamicThreshold = 1.0;

    public BlinkDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxInterval = config.getLong("detectors.blink.max_interval_ms", 150);
        this.triggerCount = config.getInt("detectors.blink.trigger_count", 10);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double x, double y, double z) {
        if (data.lastPacketTime != 0) {
            long packetInterval = timestamp - data.lastPacketTime;
            if (packetInterval > maxInterval * dynamicThreshold && packetInterval < 500) {
                data.blinkCount++;
                if (data.blinkCount >= triggerCount * dynamicThreshold && data.packetTimestamps.size() > triggerCount) {
                    handler.triggerAlert(player, AntiCheatHandler.CheatType.BLINK,
                            "Packet Interval: " + packetInterval + "ms, Count: " + data.blinkCount);
                    data.blinkCount = 0;
                }
            } else if (packetInterval <= maxInterval) {
                data.blinkCount = Math.max(0, data.blinkCount - 1);
            }
        }
        data.lastPacketTime = timestamp;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, float yaw, float pitch) {}

    @Override
    public void setDynamicThreshold(double threshold) {
        this.dynamicThreshold = threshold;
    }
}