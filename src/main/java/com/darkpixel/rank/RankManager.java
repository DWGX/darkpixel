package com.darkpixel.rank;

import com.darkpixel.Global;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
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
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM `groups`");
             ResultSet rs = ps.executeQuery()) {
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
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_groups");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                playerGroups.computeIfAbsent(uuid, k -> new ArrayList<>()).add(rs.getString("group_name"));
            }
        }
    }

    private void loadAllRanks() throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM players");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                RankData data = new RankData(rs.getString("rank") != null ? rs.getString("rank") : "member", rs.getInt("score"));
                data.setJoinParticle(rs.getString("join_particle") != null ? Particle.valueOf(rs.getString("join_particle")) : Particle.FIREWORK);
                data.setJoinMessage(rs.getString("join_message") != null ? rs.getString("join_message") : "欢迎 {player} 加入服务器！");
                data.setChatColor(rs.getString("chat_color") != null ? rs.getString("chat_color") : "normal");
                data.setShowRank(rs.getBoolean("show_rank"));
                data.setShowGroup(rs.getBoolean("show_group"));
                data.setShowScore(rs.getBoolean("show_score"));
                data.setBanUntil(rs.getLong("ban_until"));
                data.setBanReason(rs.getString("ban_reason"));
                String displayOrder = rs.getString("display_order");
                data.setDisplayOrder(displayOrder != null ? Arrays.asList(displayOrder.split(",")) : Arrays.asList("score", "group", "rank"));
                List<String> groups = new ArrayList<>();
                try (PreparedStatement groupPs = conn.prepareStatement("SELECT group_name FROM player_groups WHERE uuid = ?")) {
                    groupPs.setString(1, uuid.toString());
                    ResultSet groupRs = groupPs.executeQuery();
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
        RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
        data.setRank(rank);
        data.setScore(score);
        data.setJoinParticle(particle);
        data.setJoinMessage(joinMessage);
        allRanks.put(uuid, data);
        Global.executor.submit(() -> saveRankToDatabase(data, uuid));
        updatePlayerDisplay(player);
        context.getChatListener().updateCache(player);
    }

    public void setRankByUUID(UUID uuid, String rank, int score, Particle particle, String joinMessage) {
        RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
        data.setRank(rank);
        data.setScore(score);
        data.setJoinParticle(particle);
        data.setJoinMessage(joinMessage);
        allRanks.put(uuid, data);
        Global.executor.submit(() -> saveRankToDatabase(data, uuid));
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) updatePlayerDisplay(player);
    }

    public void setScoreByUUID(UUID uuid, int score) {
        RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
        data.setScore(score);
        allRanks.put(uuid, data);
        Global.executor.submit(() -> saveRankToDatabase(data, uuid));
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) context.getChatListener().updateCache(player);
    }

    public void setGroup(Player player, String group) {
        UUID uuid = player.getUniqueId();
        setPlayerGroups(uuid, Collections.singletonList(group));
        updatePlayerDisplay(player);
        context.getChatListener().updateCache(player);
    }

    public void setGroupByUUID(UUID uuid, String group) {
        setPlayerGroups(uuid, Collections.singletonList(group));
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            updatePlayerDisplay(player);
            context.getChatListener().updateCache(player);
        }
    }

    private void setPlayerGroups(UUID uuid, List<String> newGroups) {
        List<String> validGroups = new ArrayList<>();
        for (String group : newGroups) {
            if (groups.containsKey(group)) validGroups.add(group);
        }
        if (validGroups.isEmpty()) validGroups.add("member");
        playerGroups.put(uuid, validGroups);
        String playerName = Bukkit.getOfflinePlayer(uuid).getName();
        if (playerName == null) playerName = "Unknown";
        final String finalPlayerName = playerName;
        Global.executor.submit(() -> {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_groups WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO player_groups (uuid, group_name, player_name) VALUES (?, ?, ?)")) {
                    for (String group : validGroups) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, group);
                        ps.setString(3, finalPlayerName);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                Bukkit.getLogger().severe("Failed to save player groups: " + e.getMessage());
            }
        });
    }

    private void saveRankToDatabase(RankData data, UUID uuid) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO players (uuid, name, `rank`, score, join_particle, join_message, chat_color, show_rank, show_group, show_score, ban_until, ban_reason, login_count, sign_in_count, last_sign_in, x, y, z, world, effects_enabled, particle, display_order) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                                 "`rank` = ?, score = ?, join_particle = ?, join_message = ?, chat_color = ?, show_rank = ?, show_group = ?, show_score = ?, ban_until = ?, ban_reason = ?, login_count = ?, sign_in_count = ?, last_sign_in = ?, x = ?, y = ?, z = ?, world = ?, effects_enabled = ?, particle = ?, display_order = ?")) {
                conn.setAutoCommit(true);
                String playerName = Bukkit.getOfflinePlayer(uuid).getName();
                com.darkpixel.utils.PlayerData.PlayerInfo info = context.getPlayerData().getPlayerInfo(playerName);
                int login_count = info != null ? info.login_count : 0;
                int sign_in_count = info != null ? info.sign_in_count : 0;
                long last_sign_in = info != null ? info.last_sign_in : 0;
                Location loc = info != null && info.location != null ? info.location : new Location(Bukkit.getWorld("world"), 0, 0, 0);
                String displayOrder = String.join(",", data.getDisplayOrder());

                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, data.getRank());
                ps.setInt(4, data.getScore());
                ps.setString(5, data.getJoinParticle().name());
                ps.setString(6, data.getJoinMessage());
                ps.setString(7, data.getChatColor());
                ps.setBoolean(8, data.isShowRank());
                ps.setBoolean(9, data.isShowGroup());
                ps.setBoolean(10, data.isShowScore());
                ps.setLong(11, data.getBanUntil());
                ps.setString(12, data.getBanReason());
                ps.setInt(13, login_count);
                ps.setInt(14, sign_in_count);
                ps.setLong(15, last_sign_in);
                ps.setDouble(16, loc.getX());
                ps.setDouble(17, loc.getY());
                ps.setDouble(18, loc.getZ());
                ps.setString(19, loc.getWorld().getName());
                ps.setBoolean(20, info != null && info.effects_enabled);
                ps.setString(21, info != null && info.particle != null ? info.particle.name() : null);
                ps.setString(22, displayOrder);
                ps.setString(23, data.getRank());
                ps.setInt(24, data.getScore());
                ps.setString(25, data.getJoinParticle().name());
                ps.setString(26, data.getJoinMessage());
                ps.setString(27, data.getChatColor());
                ps.setBoolean(28, data.isShowRank());
                ps.setBoolean(29, data.isShowGroup());
                ps.setBoolean(30, data.isShowScore());
                ps.setLong(31, data.getBanUntil());
                ps.setString(32, data.getBanReason());
                ps.setInt(33, login_count);
                ps.setInt(34, sign_in_count);
                ps.setLong(35, last_sign_in);
                ps.setDouble(36, loc.getX());
                ps.setDouble(37, loc.getY());
                ps.setDouble(38, loc.getZ());
                ps.setString(39, loc.getWorld().getName());
                ps.setBoolean(40, info != null && info.effects_enabled);
                ps.setString(41, info != null && info.particle != null ? info.particle.name() : null);
                ps.setString(42, displayOrder);
                ps.executeUpdate();
                return;
            } catch (SQLException e) {
                Bukkit.getLogger().severe("Failed to save rank for " + uuid + " (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
                if (e.getMessage().contains("Deadlock") && attempt < maxRetries) {
                    try {
                        Thread.sleep(100 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    break;
                }
            }
        }
    }

    void updatePlayerDisplay(Player player) {
        Bukkit.getScheduler().runTask(context.getPlugin(), () -> {
            player.setDisplayName(getDisplayName(player));
            player.setPlayerListName(getDisplayName(player));
        });
    }

    private String getDisplayName(Player player) {
        UUID uuid = player.getUniqueId();
        RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
        List<String> groups = playerGroups.getOrDefault(uuid, Collections.singletonList("member"));
        String groupPrefix = groups.isEmpty() ? "[Member]" : this.groups.get(groups.get(0)).getPrefix();
        StringBuilder display = new StringBuilder(data.getChatColor());
        int stars = data.getScore() / 1000; // 每1000分一颗星
        String starString = stars > 0 ? "§6" + "★".repeat(Math.min(stars, 5)) : ""; // 最多5颗星

        for (String part : data.getDisplayOrder()) {
            switch (part) {
                case "score":
                    if (data.isShowScore()) display.append("[").append(data.getScore()).append("]");
                    break;
                case "group":
                    if (data.isShowGroup()) display.append("[").append(groupPrefix).append("]");
                    break;
                case "rank":
                    if (data.isShowRank()) display.append("[").append(data.getRank()).append("]");
                    break;
            }
        }
        display.append(starString).append(player.getName());
        return display.toString();
    }

    public void saveAll() {
        for (Map.Entry<UUID, RankData> entry : allRanks.entrySet()) {
            Global.executor.submit(() -> saveRankToDatabase(entry.getValue(), entry.getKey()));
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
            Bukkit.getLogger().severe("Failed to reload rank data: " + e.getMessage());
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

    public List<String> getPlayerGroups(UUID uuid) {
        return playerGroups.getOrDefault(uuid, Collections.singletonList("member"));
    }

    public List<String> getPlayerGroups(Player player) {
        if (player == null) return Collections.singletonList("member");
        return getPlayerGroups(player.getUniqueId());
    }

    public String getRank(Player player) {
        if (player == null) return "member";
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).getRank();
    }

    public int getScore(Player player) {
        if (player == null) return 0;
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).getScore();
    }

    public Particle getJoinParticle(Player player) {
        if (player == null) return Particle.FIREWORK;
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).getJoinParticle();
    }

    public String getJoinMessage(Player player) {
        if (player == null) return "欢迎 {player} 加入服务器！";
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).getJoinMessage();
    }

    public String getChatColor(Player player) {
        if (player == null) return "normal";
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).getChatColor();
    }

    public boolean isShowRank(Player player) {
        if (player == null) return false;
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).isShowRank();
    }

    public boolean isShowGroup(Player player) {
        if (player == null) return true;
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).isShowGroup();
    }

    public boolean isShowScore(Player player) {
        if (player == null) return true;
        return allRanks.getOrDefault(player.getUniqueId(), new RankData("member", 0)).isShowScore();
    }

    public void setDisplayOrder(UUID uuid, List<String> order) {
        RankData data = allRanks.getOrDefault(uuid, new RankData("member", 0));
        data.setDisplayOrder(order);
        allRanks.put(uuid, data);
        Global.executor.submit(() -> saveRankToDatabase(data, uuid));
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            updatePlayerDisplay(player);
            context.getChatListener().updateCache(player);
        }
    }
}