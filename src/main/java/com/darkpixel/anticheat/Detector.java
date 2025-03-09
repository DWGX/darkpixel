package com.darkpixel.anticheat;
import org.bukkit.entity.Player;
public interface Detector {
    void check(Player player, PlayerCheatData data, long timestamp, double x, double y, double z);
    void check(Player player, PlayerCheatData data, long timestamp, float yaw, float pitch);
    void setDynamicThreshold(double threshold);
}