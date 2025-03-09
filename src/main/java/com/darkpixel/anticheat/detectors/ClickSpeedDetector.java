package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.AntiCheatHandler.Detector;
import com.darkpixel.anticheat.AntiCheatHandler.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class ClickSpeedDetector implements Detector {
    private final double maxCps;
    private final AntiCheatHandler handler;
    private double dynamicThreshold = 1.0;

    public ClickSpeedDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxCps = config.getDouble("detectors.click_speed.max_cps", 20.0);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double x, double y, double z) {
        data.clickTimes.add(timestamp);
        data.clickTimes.removeIf(t -> timestamp - t > 1000);
        int cps = data.clickTimes.size();
        if (cps > maxCps * dynamicThreshold) {
            handler.triggerAlert(player, AntiCheatHandler.CheatType.HIGH_CPS, "CPS: " + cps);
        }
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, float yaw, float pitch) {}

    @Override
    public void setDynamicThreshold(double threshold) {
        this.dynamicThreshold = threshold;
    }
}