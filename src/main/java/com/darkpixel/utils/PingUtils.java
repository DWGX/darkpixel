package com.darkpixel.utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
public class PingUtils {
    private static final int[] THRESHOLDS = {50, 100, 200, 300};
    public static int getPlayerPing(Player player) {
        return player.getPing();
    }
    public static Component formatPingMessage(String playerName, int ping) {
        NamedTextColor color;
        String quality;
        if (ping < THRESHOLDS[0]) {
            color = NamedTextColor.GREEN;
            quality = "极佳";
        } else if (ping < THRESHOLDS[1]) {
            color = NamedTextColor.YELLOW;
            quality = "良好";
        } else if (ping < THRESHOLDS[2]) {
            color = NamedTextColor.GOLD;
            quality = "一般";
        } else if (ping < THRESHOLDS[3]) {
            color = NamedTextColor.RED;
            quality = "较差";
        } else {
            color = NamedTextColor.DARK_RED;
            quality = "极差";
        }
        return Component.text(playerName + " 的延迟为 ", NamedTextColor.GREEN)
                .append(Component.text(ping + "ms (" + quality + ")", color));
    }
}