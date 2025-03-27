package com.darkpixel.anticheat;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PlayerCheatData {
    public long lastPacketTime = 0;
    public long lastAlertTime = 0;
    public ConcurrentLinkedDeque<Long> packetTimestamps = new ConcurrentLinkedDeque<>();
    public Map<PacketTypeCommon, Integer> packetCounts = new HashMap<>();
    public int totalPackets = 0;

    public void incrementPacketCount(PacketTypeCommon type) {
        packetCounts.merge(type, 1, Integer::sum);
        totalPackets++;
    }

    public double getAverageBps() {
        long now = System.currentTimeMillis();
        packetTimestamps.removeIf(t -> now - t > 5000);
        return packetTimestamps.isEmpty() ? 0.0 : packetTimestamps.size() / 5.0;
    }
}