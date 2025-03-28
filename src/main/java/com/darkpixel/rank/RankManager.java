package com.darkpixel.rank;

import com.darkpixel.Global;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        Global.executor.submit(this::reload);
    }

    public Connection getConnection() throws SQLException {
        YamlConfiguration config = context.getConfigManager().getConfig();
        String url = "jdbc:mysql://" + config.getString("mysql.host") + ":" + config.getInt("mysql.port") + "/" + config.getString("mysql.database") + "?autoReconnect=true";
        return DriverManager.getConnection(url, config.getString("mysql.username"), config.getString("mysql.password"));
    }

    private void loadGroups() throws SQLException {
        try (Connection conn = getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM `groups`")) {
            while (rs.next()) {
                groups.put(rs.getString("name"), new RankGroup(rs.getString("name"), rs.getString("color"), rs.getString("emoji"), rs.getString("badge"), rs.getString("prefix")));
            }
            if (!groups.containsKey("op")) groups.put("op", new RankGroup("op", "§c", "", "", "[OP]"));
            if (!groups.containsKey("member")) groups.put("member", new RankGroup("member", "§f", "", "", "[Member]"));
            if (!groups.containsKey("banned")) groups.put("banned", new RankGroup("banned", "§c", "", "", "[Banned]"));
        }
    }

    private void loadPlayerGroups() throws SQLException {
        try (Connection conn = getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM player_groups")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                playerGroups.computeIfAbsent(uuid, k -> new ArrayList<>()).add(rs.getString("group_name"));
            }
        }
    }

    private void loadAllRanks() throws SQLException {
        try (Connection conn = getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM players")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                RankData data = new RankData(rs.getString("rank") != null ? rs.getString("rank") : "member", rs.getInt("score"));
                data.setJoinParticle(rs.getString("join_particle") != null ? Particle.valueOf(rs.getString("join_particle")) : Particle.FIREWORK);
                data.setJoinMessage(rs.getString("join_message") != null ? rs.getString("join_message") : "欢迎 {player} 加入服务器！");
                data.setChatColor(rs.getString("chat_color") != null ? rs.getString("chat_color") : "normal");
                data.setShowRank(rs.getBoolean("show_rank"));
                data.setShowVip(rs.getBoolean("show_vip"));
                data.setShowGroup(rs.getBoolean("show_group"));
                data.setBanUntil(rs.getLong("ban_until"));
                data.setBanReason(rs.getString("ban_reason"));
                List<String> groups = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT group_name FROM player_groups WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet groupRs = ps.executeQuery();
                    while (groupRs.next()) {
                        groups.add(groupRs.getString("group_name"));
                    }
                }
                data.setGroups(groups.isEmpty() ? Collections.singletonList("member") : groups);
                allRanks.put(uuid, data);
            }
        }
    }

    public void setRank(Player player, String rank, int score, Particle particle, String joinMessage) {
        UUID uuid = player.getUniqueId();
        if (context.isRankServerRunning()) {
            RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
            data.rank = rank;
            data.score = score;
            data.setJoinParticle(particle);
            data.setJoinMessage(joinMessage);
            allRanks.put(uuid, data);
            Global.executor.submit(() -> saveRankToDatabase(uuid, player.getName(), data));
        } else {
            context.getRankServerClient().setRank(uuid.toString(), rank);
            RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
            data.rank = rank;
            data.score = score;
            data.setJoinParticle(particle);
            data.setJoinMessage(joinMessage);
            allRanks.put(uuid, data);
        }
    }

    public void setRankByUUID(UUID uuid, String rank, int score, Particle particle, String joinMessage) {
        if (context.isRankServerRunning()) {
            RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
            data.rank = rank;
            data.score = score;
            data.setJoinParticle(particle);
            data.setJoinMessage(joinMessage);
            allRanks.put(uuid, data);
            Global.executor.submit(() -> saveRankToDatabase(uuid, Bukkit.getOfflinePlayer(uuid).getName(), data));
        } else {
            context.getRankServerClient().setRank(uuid.toString(), rank);
            RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
            data.rank = rank;
            data.score = score;
            data.setJoinParticle(particle);
            data.setJoinMessage(joinMessage);
            allRanks.put(uuid, data);
        }
    }

    public void setScoreByUUID(UUID uuid, int score) {
        if (context.isRankServerRunning()) {
            RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
            data.score = score;
            allRanks.put(uuid, data);
            Global.executor.submit(() -> saveRankToDatabase(uuid, Bukkit.getOfflinePlayer(uuid).getName(), data));
        } else {
            context.getRankServerClient().setScore(uuid.toString(), score);
            RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
            data.score = score;
            allRanks.put(uuid, data);
        }
    }

    private void saveRankToDatabase(UUID uuid, String playerName, RankData data) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO players (uuid, name, `rank`, score, join_particle, join_message, chat_color, show_rank, show_vip, show_group, ban_until, ban_reason, loginCount) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                             "`rank` = ?, score = ?, join_particle = ?, join_message = ?, chat_color = ?, show_rank = ?, show_vip = ?, show_group = ?, ban_until = ?, ban_reason = ?, loginCount = ?")) {
            int loginCount = context.getPlayerData().getPlayerInfo(playerName).loginCount;
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
            if (groups.containsKey(group)) validGroups.add(group);
        }
        if (validGroups.isEmpty()) validGroups.add("member");
        playerGroups.put(uuid, validGroups);
        Global.executor.submit(() -> {
            try (Connection conn = getConnection()) {
                String playerName = Bukkit.getOfflinePlayer(uuid).getName();
                if (playerName != null) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_groups WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO player_groups (uuid, group_name, player_name) VALUES (?, ?, ?)")) {
                        for (String group : validGroups) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, group);
                            ps.setString(3, playerName);
                            ps.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void setGroup(Player player, String group) {
        if (context.isRankServerRunning()) {
            setPlayerGroups(player.getUniqueId(), Collections.singletonList(group));
        } else {
            context.getRankServerClient().setGroup(player.getUniqueId().toString(), group);
            setPlayerGroups(player.getUniqueId(), Collections.singletonList(group));
        }
    }

    public void setGroupByUUID(UUID uuid, String group) {
        if (context.isRankServerRunning()) {
            setPlayerGroups(uuid, Collections.singletonList(group));
        } else {
            context.getRankServerClient().setGroup(uuid.toString(), group);
            setPlayerGroups(uuid, Collections.singletonList(group));
        }
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
        try {
            loadGroups();
            loadPlayerGroups();
            loadAllRanks();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void startSyncTask() {
        Bukkit.getScheduler().runTaskTimer(context.getPlugin(), this::reload, 0L, 20L * 60L * 5L);
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