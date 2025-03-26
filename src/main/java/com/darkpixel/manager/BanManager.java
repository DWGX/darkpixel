package com.darkpixel.manager;

import com.darkpixel.Global;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

public class BanManager {
    private final Global context;

    public BanManager(Global context) {
        this.context = context;
    }

    public void banPlayer(String playerName, long banUntil, String reason) {
        UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            String kickMessage = "你已被封禁" + (banUntil == -1 ? "永久" : "，剩余 " + ((banUntil - System.currentTimeMillis()) / 60000) + " 分钟") + "\n原因：" + reason;
            Bukkit.getScheduler().runTask(context.getPlugin(), () -> player.kickPlayer(kickMessage));
        }
        Date expiry = banUntil == -1 ? null : new Date(banUntil);
        Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, expiry, "Server");
        Global.executor.submit(() -> saveBanToDatabase(uuid, playerName, banUntil, reason));
    }

    public void unbanPlayer(String playerName) {
        UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        Bukkit.getBanList(BanList.Type.NAME).pardon(playerName);
        Global.executor.submit(() -> saveBanToDatabase(uuid, playerName, 0, null));
    }

    private void saveBanToDatabase(UUID uuid, String playerName, long banUntil, String reason) {
        try (Connection conn = context.getRankManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE players SET ban_until = ?, ban_reason = ? WHERE uuid = ?")) {
            ps.setLong(1, banUntil);
            ps.setString(2, reason);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}