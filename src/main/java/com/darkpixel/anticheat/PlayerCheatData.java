package com.darkpixel.anticheat;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PlayerCheatData {
    public Float lastYaw = null;
    public Float lastPitch = null;
    public Location lastLocation = null;
    public long lastPacketTime = 0;
    public long lastMoveTime = 0;
    public long lastHitTime = 0;
    public long lastAlertTime = 0;
    public int blinkCount = 0;
    public ConcurrentLinkedDeque<Long> clickTimes = new ConcurrentLinkedDeque<>(); // 线程安全
    public ConcurrentLinkedDeque<Long> packetTimestamps = new ConcurrentLinkedDeque<>(); // 线程安全
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
        long now = System.currentTimeMillis();
        packetTimestamps.removeIf(t -> now - t > 5000); // 线程安全移除
        return packetTimestamps.isEmpty() ? 0.0 : packetTimestamps.size() / 5.0; // 每 5 秒平均 BPS
    }

    public double getPeakBps() {
        long now = System.currentTimeMillis();
        Map<Long, Integer> secondCounts = new HashMap<>();
        for (long timestamp : packetTimestamps) {
            long second = (now - timestamp) / 1000;
            secondCounts.merge(second, 1, Integer::sum);
        }
        return secondCounts.values().stream().max(Integer::compare).orElse(0);
    }
}