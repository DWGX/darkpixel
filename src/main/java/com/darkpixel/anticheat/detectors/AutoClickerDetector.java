package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.util.Arrays;
import java.util.Deque;

public class AutoClickerDetector implements Detector {
    private final double maxCps;
    private final double minVariance;
    private final AntiCheatHandler handler;

    public AutoClickerDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxCps = config.getDouble("detectors.auto_clicker.max_cps", 20.0);
        this.minVariance = config.getDouble("detectors.auto_clicker.min_variance", 5.0);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        data.clickTimes.add(timestamp);
        data.clickTimes.removeIf(t -> timestamp - t > 1000);
        int cps = data.clickTimes.size();
        if (cps > maxCps) {
            double variance = calculateVariance(data.clickTimes);
            if (variance < minVariance) {
                handler.triggerAlert(player, AntiCheatHandler.CheatType.AUTO_CLICKER,
                        "CPS: " + cps + ", Variance: " + String.format("%.2f", variance));
            }
        }
    }

    private double calculateVariance(Deque<Long> times) {
        if (times.size() < 2) return Double.MAX_VALUE;
        long[] intervals = new long[times.size() - 1];
        Long prev = null;
        int index = 0;
        for (Long time : times) {
            if (prev != null) {
                intervals[index++] = time - prev;
            }
            prev = time;
        }
        double mean = Arrays.stream(intervals).average().orElse(0.0);
        double variance = Arrays.stream(intervals).mapToDouble(i -> Math.pow(i - mean, 2)).average().orElse(0.0);
        return variance;
    }
}