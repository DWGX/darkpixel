package com.darkpixel.anticheat;
import org.bukkit.entity.Player;
public interface Detector {
    void check(Player player, PlayerCheatData data, long timestamp, double... args);
}