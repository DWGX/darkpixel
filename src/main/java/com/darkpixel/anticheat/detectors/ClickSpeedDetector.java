package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class ClickSpeedDetector implements Detector {
    private final double maxCps;
    private final AntiCheatHandler handler;

    public ClickSpeedDetector(YamlConfiguration config, AntiCheatHandler handler) {
        this.maxCps = config.getDouble("detectors.click_speed.max_cps", 20.0);
        this.handler = handler;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
        data.clickTimes.add(timestamp);
        data.clickTimes.removeIf(t -> timestamp - t > 1000);
        int cps = data.clickTimes.size();
        if (cps > maxCps) {
            handler.triggerAlert(player, AntiCheatHandler.CheatType.HIGH_CPS, "CPS: " + cps);
        }
    }
}