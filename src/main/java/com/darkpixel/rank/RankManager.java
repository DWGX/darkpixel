package com.darkpixel.rank;

import com.darkpixel.Global;
import com.darkpixel.utils.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {
    private final Global context;
    private final Map<UUID, List<String>> playerGroups = new ConcurrentHashMap<>();
    private final Map<String, RankGroup> groups = new HashMap<>();
    private final Map<UUID, RankData> allRanks = new ConcurrentHashMap<>();

    public RankManager(Global context) {
        this.context = context;
        Global.executor.submit(() -> {
            loadGroups();
            loadPlayerGroups();
            loadAllRanks();
        });
    }

    public Connection getConnection() throws SQLException {
        YamlConfiguration config = context.getConfigManager().getConfig();
        String url = "jdbc:mysql://" + config.getString("mysql.host") + ":" + config.getInt("mysql.port") + "/" + config.getString("mysql.database") + "?autoReconnect=true";
        return DriverManager.getConnection(url, config.getString("mysql.username"), config.getString("mysql.password"));
    }

    private void loadGroups() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM `groups`")) {
            while (rs.next()) {
                String name = rs.getString("name");
                String color = rs.getString("color");
                String emoji = rs.getString("emoji");
                String badge = rs.getString("badge");
                String prefix = rs.getString("prefix");
                groups.put(name, new RankGroup(name, color, emoji, badge, prefix));
            }
            if (!groups.containsKey("op")) {
                groups.put("op", new RankGroup("op", "§c", "", "", "[OP]"));
                saveGroupToDatabase("op", "§c", "", "", "[OP]");
            }
            if (!groups.containsKey("member")) {
                groups.put("member", new RankGroup("member", "§f", "", "", "[Member]"));
                saveGroupToDatabase("member", "§f", "", "", "[Member]");
            }
            if (!groups.containsKey("banned")) {
                groups.put("banned", new RankGroup("banned", "§c", "", "", "[Banned]"));
                saveGroupToDatabase("banned", "§c", "", "", "[Banned]");
            }
        } catch (SQLException e) {
            groups.put("default", new RankGroup("default", "§f", "", "", "[默认]"));
            e.printStackTrace();
        }
    }

    private void saveGroupToDatabase(String name, String color, String emoji, String badge, String prefix) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO `groups` (name, color, emoji, badge, prefix) VALUES (?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE color = ?, emoji = ?, badge = ?, prefix = ?")) {
            ps.setString(1, name);
            ps.setString(2, color);
            ps.setString(3, emoji);
            ps.setString(4, badge);
            ps.setString(5, prefix);
            ps.setString(6, color);
            ps.setString(7, emoji);
            ps.setString(8, badge);
            ps.setString(9, prefix);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadPlayerGroups() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM player_groups")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String group = rs.getString("group_name");
                playerGroups.computeIfAbsent(uuid, k -> new ArrayList<>()).add(group);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadAllRanks() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM players")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String rank = rs.getString("rank") != null ? rs.getString("rank") : "member";
                int score = rs.getInt("score");
                String particle = rs.getString("join_particle");
                String message = rs.getString("join_message");
                String chatColor = rs.getString("chat_color");
                boolean showRank = rs.getBoolean("show_rank");
                boolean showVip = rs.getBoolean("show_vip");
                boolean showGroup = rs.getBoolean("show_group");
                long banUntil = rs.getLong("ban_until");
                String banReason = rs.getString("ban_reason");
                RankData data = new RankData(rank, score);
                data.setJoinParticle(particle != null ? Particle.valueOf(particle) : Particle.FIREWORK);
                data.setJoinMessage(message != null ? message : "欢迎 {player} 加入服务器！");
                data.setChatColor(chatColor != null ? chatColor : "normal");
                data.setShowRank(showRank);
                data.setShowVip(showVip);
                data.setShowGroup(showGroup);
                data.setBanUntil(banUntil);
                data.setBanReason(banReason);
                List<String> groups = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT group_name FROM player_groups WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet groupRs = ps.executeQuery();
                    while (groupRs.next()) {
                        groups.add(groupRs.getString("group_name"));
                    }
                }
                if (groups.isEmpty()) groups.add("member");
                data.setGroups(groups);
                allRanks.put(uuid, data);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setRank(Player player, String rank, int score, Particle particle, String joinMessage) {
        UUID uuid = player.getUniqueId();
        RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
        data.rank = rank;
        data.score = score;
        data.setJoinParticle(particle);
        data.setJoinMessage(joinMessage);
        allRanks.put(uuid, data);
        Global.executor.submit(() -> saveRankToDatabase(uuid, player.getName(), data));
    }

    public void setRankByUUID(UUID uuid, String rank, int score, Particle particle, String joinMessage) {
        RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
        data.rank = rank;
        data.score = score;
        data.setJoinParticle(particle);
        data.setJoinMessage(joinMessage);
        allRanks.put(uuid, data);
        Global.executor.submit(() -> saveRankToDatabase(uuid, Bukkit.getOfflinePlayer(uuid).getName(), data));
    }

    public void setScoreByUUID(UUID uuid, int score) {
        RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
        data.score = score;
        allRanks.put(uuid, data);
        Global.executor.submit(() -> saveRankToDatabase(uuid, Bukkit.getOfflinePlayer(uuid).getName(), data));
    }

    private void saveRankToDatabase(UUID uuid, String playerName, RankData data) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO players (uuid, name, `rank`, score, join_particle, join_message, chat_color, show_rank, show_vip, show_group, ban_until, ban_reason, loginCount) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                             "`rank` = ?, score = ?, join_particle = ?, join_message = ?, chat_color = ?, show_rank = ?, show_vip = ?, show_group = ?, ban_until = ?, ban_reason = ?, loginCount = ?")) {
            PlayerData.PlayerInfo playerInfo = context.getPlayerData().getPlayerInfo(playerName);
            int loginCount = (playerInfo != null) ? playerInfo.loginCount : 0;
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, data.getRank());
            ps.setInt(4, data.getScore());
            ps.setString(5, data.getJoinParticle().name());
            ps.setString(6, data.getJoinMessage());
            ps.setString(7, data.getChatColor());
            ps.setBoolean(8, data.isShowRank());
            ps.setBoolean(9, data.isShowVip());
            ps.setBoolean(10, data.isShowGroup());
            ps.setLong(11, data.getBanUntil());
            ps.setString(12, data.getBanReason());
            ps.setInt(13, loginCount);
            ps.setString(14, data.getRank());
            ps.setInt(15, data.getScore());
            ps.setString(16, data.getJoinParticle().name());
            ps.setString(17, data.getJoinMessage());
            ps.setString(18, data.getChatColor());
            ps.setBoolean(19, data.isShowRank());
            ps.setBoolean(20, data.isShowVip());
            ps.setBoolean(21, data.isShowGroup());
            ps.setLong(22, data.getBanUntil());
            ps.setString(23, data.getBanReason());
            ps.setInt(24, loginCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setPlayerGroups(UUID uuid, List<String> newGroups) {
        List<String> validGroups = new ArrayList<>();
        for (String group : newGroups) {
            if (groups.containsKey(group)) {
                validGroups.add(group);
            }
        }
        if (validGroups.isEmpty()) {
            validGroups.add("member");
        }
        playerGroups.put(uuid, validGroups);
        Global.executor.submit(() -> {
            try (Connection conn = getConnection()) {
                String playerName = context.getPlugin().getServer().getOfflinePlayer(uuid).getName();
                if (playerName != null) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM players WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ResultSet rs = ps.executeQuery();
                        if (rs.next() && rs.getInt(1) == 0) {
                            try (PreparedStatement insertPs = conn.prepareStatement(
                                    "INSERT INTO players (uuid, name, `rank`, score, join_particle, join_message, chat_color, show_rank, show_vip, show_group, ban_until, ban_reason, loginCount) " +
                                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                                insertPs.setString(1, uuid.toString());
                                insertPs.setString(2, playerName);
                                insertPs.setString(3, "member");
                                insertPs.setInt(4, 0);
                                insertPs.setString(5, Particle.FIREWORK.name());
                                insertPs.setString(6, "欢迎 {player} 加入服务器！");
                                insertPs.setString(7, "normal");
                                insertPs.setBoolean(8, true);
                                insertPs.setBoolean(9, true);
                                insertPs.setBoolean(10, false);
                                insertPs.setLong(11, 0);
                                insertPs.setString(12, null);
                                insertPs.setInt(13, 0);
                                insertPs.executeUpdate();
                            }
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_groups WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO player_groups (uuid, group_name, player_name) VALUES (?, ?, ?)")) {
                    for (String group : validGroups) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, group);
                        ps.setString(3, playerName != null ? playerName : "");
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void setGroup(Player player, String group) {
        setPlayerGroups(player.getUniqueId(), Collections.singletonList(group));
    }

    public void setGroupByUUID(UUID uuid, String group) {
        setPlayerGroups(uuid, Collections.singletonList(group));
    }

    public void saveAll() {
        for (Map.Entry<UUID, RankData> entry : allRanks.entrySet()) {
            saveRankToDatabase(entry.getKey(), Bukkit.getOfflinePlayer(entry.getKey()).getName(), entry.getValue());
        }
    }

    public void reload() {
        playerGroups.clear();
        groups.clear();
        allRanks.clear();
        loadGroups();
        loadPlayerGroups();
        loadAllRanks();
    }

    public Map<UUID, RankData> getAllRanks() {
        return allRanks;
    }

    public Map<String, RankGroup> getGroups() {
        return groups;
    }

    public List<String> getPlayerGroups(Player player) {
        return playerGroups.getOrDefault(player != null ? player.getUniqueId() : UUID.randomUUID(), Collections.singletonList("member"));
    }

    public String getRank(Player player) {
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).getRank();
    }

    public int getScore(Player player) {
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).getScore();
    }

    public Particle getJoinParticle(Player player) {
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).getJoinParticle();
    }

    public String getJoinMessage(Player player) {
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).getJoinMessage();
    }

    public String getChatColor(Player player) {
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).getChatColor();
    }

    public boolean isShowRank(Player player) {
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).isShowRank();
    }

    public boolean isShowVip(Player player) {
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).isShowVip();
    }

    public boolean isShowGroup(Player player) {
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).isShowGroup();
    }
}