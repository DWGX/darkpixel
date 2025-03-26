package com.darkpixel.rank;

import com.darkpixel.Global;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
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
            context.getPlugin().getLogger().info("[RankManager] 异步加载完成");
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
        } catch (SQLException e) {
            context.getPlugin().getLogger().severe("加载身份组失败: " + e.getMessage());
            groups.put("default", new RankGroup("default", "§f", "", "", "[默认]"));
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
            context.getPlugin().getLogger().severe("加载玩家身份组失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadAllRanks() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, `rank`, score, join_particle, join_message FROM players")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String rank = rs.getString("rank");
                int score = rs.getInt("score");
                String particle = rs.getString("join_particle");
                String message = rs.getString("join_message");
                RankData data = new RankData(rank, score);
                data.setJoinParticle(particle != null ? Particle.valueOf(particle) : Particle.FIREWORK);
                data.setJoinMessage(message != null ? message : "欢迎 {player} 加入服务器！");
                allRanks.put(uuid, data);
            }
        } catch (SQLException e) {
            context.getPlugin().getLogger().severe("加载玩家 Rank 数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setRank(Player player, String rank, int score, Particle particle, String joinMessage) {
        UUID uuid = player.getUniqueId();
        RankData data = new RankData(rank, score);
        data.setJoinParticle(particle);
        data.setJoinMessage(joinMessage);
        allRanks.put(uuid, data);
        Global.executor.submit(() -> saveRankToDatabase(uuid, rank, score, particle, joinMessage));
    }

    private void saveRankToDatabase(UUID uuid, String rank, int score, Particle particle, String joinMessage) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO players (uuid, `rank`, score, join_particle, join_message) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `rank` = ?, score = ?, join_particle = ?, join_message = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, rank);
            ps.setInt(3, score);
            ps.setString(4, particle.name());
            ps.setString(5, joinMessage);
            ps.setString(6, rank);
            ps.setInt(7, score);
            ps.setString(8, particle.name());
            ps.setString(9, joinMessage);
            ps.executeUpdate();
        } catch (SQLException e) {
            context.getPlugin().getLogger().severe("保存 Rank 数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<String> getPlayerGroups(Player player) {
        return playerGroups.getOrDefault(player.getUniqueId(), Collections.singletonList("default"));
    }

    public void setPlayerGroups(UUID uuid, List<String> newGroups) {
        playerGroups.put(uuid, newGroups);
        Global.executor.submit(() -> {
            try (Connection conn = getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_groups WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO player_groups (uuid, group_name, player_name) VALUES (?, ?, ?)")) {
                    String playerName = context.getPlugin().getServer().getOfflinePlayer(uuid).getName();
                    for (String group : newGroups) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, group);
                        ps.setString(3, playerName != null ? playerName : "");
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (SQLException e) {
                context.getPlugin().getLogger().severe("设置玩家身份组失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public Map<String, RankGroup> getGroups() {
        return groups;
    }

    public String getRank(Player player) {
        RankData rankData = allRanks.getOrDefault(player.getUniqueId(), null);
        return rankData != null ? rankData.getRank() : "default";
    }

    public int getScore(Player player) {
        RankData rankData = allRanks.getOrDefault(player.getUniqueId(), null);
        return rankData != null ? rankData.getScore() : 0;
    }

    public Particle getJoinParticle(Player player) {
        RankData rankData = allRanks.getOrDefault(player.getUniqueId(), null);
        return rankData != null ? rankData.getJoinParticle() : Particle.FIREWORK;
    }

    public String getJoinMessage(Player player) {
        RankData rankData = allRanks.getOrDefault(player.getUniqueId(), null);
        return rankData != null ? rankData.getJoinMessage().replace("{player}", player.getName()) : "欢迎 " + player.getName() + " 加入服务器！";
    }

    public void saveAll() {
        context.getPlayerData().saveData();
    }

    public void reload() {
        groups.clear();
        playerGroups.clear();
        allRanks.clear();
        loadGroups();
        loadPlayerGroups();
        loadAllRanks();
    }

    public Map<UUID, RankData> getAllRanks() {
        return Collections.unmodifiableMap(allRanks);
    }

    public void setRankByUUID(UUID uuid, String rank, int score, Particle particle, String joinMessage) {
        Player player = context.getPlugin().getServer().getPlayer(uuid);
        if (player != null) {
            setRank(player, rank, score, particle, joinMessage);
        } else {
            RankData data = new RankData(rank, score);
            data.setJoinParticle(particle);
            data.setJoinMessage(joinMessage);
            allRanks.put(uuid, data);
            Global.executor.submit(() -> saveRankToDatabase(uuid, rank, score, particle, joinMessage));
        }
    }

    public void setGroup(Player player, String group) {
        List<String> groups = new ArrayList<>(getPlayerGroups(player));
        if (!groups.contains(group)) {
            groups.add(group);
            setPlayerGroups(player.getUniqueId(), groups);
        }
    }
}