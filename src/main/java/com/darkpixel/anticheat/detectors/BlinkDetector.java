package com.darkpixel.anticheat.detectors;
import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.CheatType;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
public class BlinkDetector implements Detector {
    private final long maxInterval;
    private final int triggerCount;
    private final AntiCheatHandler handler;
    public BlinkDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxInterval = config.getLong("detectors.blink.max_interval_ms", 150); 
        this.triggerCount = config.getInt("detectors.blink.trigger_count", 10); 
        this.handler = handler;
    }
    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        if (data.lastPacketTime != 0) {
            long packetInterval = timestamp - data.lastPacketTime;
            if (packetInterval > maxInterval && packetInterval < 500) {
                data.blinkCount++;
                if (data.blinkCount >= triggerCount && data.packetTimestamps.size() > triggerCount) {
                    handler.triggerAlert(player, CheatType.BLINK,
                            "Packet Interval: " + packetInterval + "ms, Count: " + data.blinkCount);
                    data.blinkCount = 0; 
                }
            } else if (packetInterval <= maxInterval) {
                data.blinkCount = Math.max(0, data.blinkCount - 1); 
            }
        }
        data.lastPacketTime = timestamp;
    }
}