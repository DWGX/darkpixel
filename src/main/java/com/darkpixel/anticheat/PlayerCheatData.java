package com.darkpixel.anticheat;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import org.bukkit.Location;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class PlayerCheatData {
    public Float lastYaw = null;
    public Float lastPitch = null;
    public Location lastLocation = null;
    public long lastPacketTime = 0;
    public long lastMoveTime = 0;
    public long lastHitTime = 0;
    public long lastAlertTime = 0;
    public int blinkCount = 0;
    public Deque<Long> clickTimes = new ArrayDeque<>(50);
    public Deque<Long> packetTimestamps = new ArrayDeque<>(100);
    public Map<PacketTypeCommon, Integer> packetCounts = new HashMap<>();
    public int totalPackets = 0;
    public int anomalyCount = 0;
    public long lastGroundTime = 0;
    public double lastVerticalSpeed = 0.0;

    public void incrementPacketCount(PacketTypeCommon type) {
        packetCounts.merge(type, 1, Integer::sum);
        totalPackets++;
    }

    public double getAverageBps() {
        if (packetTimestamps.isEmpty()) return 0.0;
        long now = System.currentTimeMillis();
        packetTimestamps.removeIf(t -> now - t > 5000);
        return packetTimestamps.size() / 5.0;
    }

    public double getPeakBps() {
        if (packetTimestamps.isEmpty()) return 0.0;
        long now = System.currentTimeMillis();
        Map<Long, Integer> secondCounts = new HashMap<>();
        for (long timestamp : packetTimestamps) {
            long second = (now - timestamp) / 1000;
            secondCounts.merge(second, 1, Integer::sum);
        }
        return secondCounts.values().stream().max(Integer::compare).orElse(0);
    }
}