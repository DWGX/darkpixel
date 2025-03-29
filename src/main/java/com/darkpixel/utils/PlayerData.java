package com.darkpixel.utils;

import com.darkpixel.Global;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.Particle;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerData {
    private final Map<String, PlayerInfo> playerData = new HashMap<>();
    private final File file;
    private YamlConfiguration config;
    public final Global context;

    public PlayerData(Global context) {
        this.context = context;
        file = new File(context.getPlugin().getDataFolder(), "player.yml");
        config = FileUtil.loadOrCreate(file, context.getPlugin(), "player.yml");
        loadData();
    }

    private Connection getConnection() throws SQLException {
        YamlConfiguration config = context.getConfigManager().getConfig();
        String host = config.getString("mysql.host");
        int port = config.getInt("mysql.port");
        String database = config.getString("mysql.database");
        String username = config.getString("mysql.username");
        String password = config.getString("mysql.password");
        String url = String.format("jdbc:mysql://%s:%d/%s?autoReconnect=true", host, port, database);
        return DriverManager.getConnection(url, username, password);
    }

    private void loadData() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM players")) {
            while (rs.next()) {
                String name = rs.getString("name");
                String uuid = rs.getString("uuid");
                int login_count = rs.getInt("login_count");
                int sign_in_count = rs.getInt("sign_in_count");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                String world = rs.getString("world");
                long last_sign_in = rs.getLong("last_sign_in");
                boolean effects_enabled = rs.getBoolean("effects_enabled");
                String particle = rs.getString("particle");
                PlayerInfo info = new PlayerInfo(login_count, new Location(context.getPlugin().getServer().getWorld(world), x, y, z));
                info.sign_in_count = sign_in_count;
                info.last_sign_in = last_sign_in;
                info.effects_enabled = effects_enabled;
                info.particle = particle != null ? Particle.valueOf(particle) : Particle.FIREWORK;
                try (PreparedStatement ps = conn.prepareStatement("SELECT group_name FROM player_groups WHERE uuid = ?")) {
                    ps.setString(1, uuid);
                    ResultSet groupRs = ps.executeQuery();
                    while (groupRs.next()) {
                        info.groups.add(groupRs.getString("group_name"));
                    }
                }
                if (info.groups.isEmpty()) info.groups.add("member");
                playerData.put(name, info);
            }
        } catch (SQLException e) {
            context.getPlugin().getLogger().severe("加载玩家数据失败: " + e.getMessage());
            loadDataFromYaml();
        }
    }

    private void loadDataFromYaml() {
        for (String key : config.getKeys(false)) {
            int login_count = config.getInt(key + ".login_count", 0);
            int sign_in_count = config.getInt(key + ".sign_in_Count", 0);
            double x = config.getDouble(key + ".x", 0);
            double y = config.getDouble(key + ".y", 0);
            double z = config.getDouble(key + ".z", 0);
            String world = config.getString(key + ".world", "world");
            long last_sign_in = config.getLong(key + ".last_sign_in", 0L);
            boolean effects_enabled = config.getBoolean(key + ".effects_enabled", true);
            String particle = config.getString(key + ".particle", "FIREWORK");
            PlayerInfo info = new PlayerInfo(login_count, new Location(context.getPlugin().getServer().getWorld(world), x, y, z));
            info.sign_in_count = sign_in_count;
            info.last_sign_in = last_sign_in;
            info.effects_enabled = effects_enabled;
            info.particle = Particle.valueOf(particle);
            if (info.groups.isEmpty()) info.groups.add("member");
            playerData.put(key, info);
        }
    }

    public void saveData() {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                for (Map.Entry<String, PlayerInfo> entry : playerData.entrySet()) {
                    String name = entry.getKey();
                    PlayerInfo info = entry.getValue();
                    Player player = context.getPlugin().getServer().getPlayer(name);
                    String uuid = player != null ? player.getUniqueId().toString() :
                            context.getPlugin().getServer().getOfflinePlayer(name).getUniqueId().toString();

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO players (uuid, name, login_count, sign_in_count, x, y, z, world, last_sign_in, effects_enabled, particle) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                                    "name = ?, login_count = ?, sign_in_count = ?, x = ?, y = ?, z = ?, world = ?, last_sign_in = ?, effects_enabled = ?, particle = ?")) {
                        ps.setString(1, uuid);
                        ps.setString(2, name);
                        ps.setInt(3, info.login_count);
                        ps.setInt(4, info.sign_in_count);
                        ps.setDouble(5, info.location.getX());
                        ps.setDouble(6, info.location.getY());
                        ps.setDouble(7, info.location.getZ());
                        ps.setString(8, info.location.getWorld().getName());
                        ps.setLong(9, info.last_sign_in);
                        ps.setBoolean(10, info.effects_enabled);
                        ps.setString(11, info.particle.name());
                        ps.setString(12, name);
                        ps.setInt(13, info.login_count);
                        ps.setInt(14, info.sign_in_count);
                        ps.setDouble(15, info.location.getX());
                        ps.setDouble(16, info.location.getY());
                        ps.setDouble(17, info.location.getZ());
                        ps.setString(18, info.location.getWorld().getName());
                        ps.setLong(19, info.last_sign_in);
                        ps.setBoolean(20, info.effects_enabled);
                        ps.setString(21, info.particle.name());
                        ps.executeUpdate();
                    }

                    try (PreparedStatement deletePs = conn.prepareStatement("DELETE FROM player_groups WHERE uuid = ?")) {
                        deletePs.setString(1, uuid);
                        deletePs.executeUpdate();
                    }
                    try (PreparedStatement insertPs = conn.prepareStatement(
                            "INSERT IGNORE INTO player_groups (uuid, group_name) VALUES (?, ?)")) {
                        for (String group : new HashSet<>(info.groups)) {
                            insertPs.setString(1, uuid);
                            insertPs.setString(2, group);
                            insertPs.addBatch();
                        }
                        insertPs.executeBatch();
                    }
                }
                conn.commit();
                return;
            } catch (SQLException e) {
                context.getPlugin().getLogger().severe("保存玩家数据失败 (尝试 " + attempt + "/" + maxRetries + "): " + e.getMessage());
                if (e.getMessage().contains("Deadlock") && attempt < maxRetries) {
                    try {
                        Thread.sleep(100 * attempt);
                        continue;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                saveDataAsync();
                break;
            }
        }
    }

    private void saveDataAsync() {
        for (Map.Entry<String, PlayerInfo> entry : playerData.entrySet()) {
            String name = entry.getKey();
            PlayerInfo info = entry.getValue();
            config.set(name + ".login_count", info.login_count);
            config.set(name + ".sign_in_count", info.sign_in_count);
            config.set(name + ".x", info.location.getX());
            config.set(name + ".y", info.location.getY());
            config.set(name + ".z", info.location.getZ());
            config.set(name + ".world", info.location.getWorld().getName());
            config.set(name + ".last_sign_in", info.last_sign_in);
            config.set(name + ".effects_enabled", info.effects_enabled);
            config.set(name + ".particle", info.particle.name());
        }
        FileUtil.saveAsync(config, file, context.getPlugin());
    }

    public void setBanStatus(String playerName, long banUntil, String reason) {
        PlayerInfo info = getPlayerInfo(playerName);
        info.groups.clear();
        if (banUntil == 0) {
            info.groups.add("member");
        } else {
            info.groups.add("banned");
        }
        saveData();
    }

    public int getsign_in_count(Player player) {
        return getPlayerInfo(player.getName()).sign_in_count;
    }

    public void setsign_in_count(Player player, int count) {
        PlayerInfo info = getPlayerInfo(player.getName());
        info.sign_in_count = count;
        saveData();
    }

    public void updatePlayer(Player player) {
        String name = player.getName();
        PlayerInfo info = playerData.getOrDefault(name, new PlayerInfo(0, player.getLocation()));
        info.login_count++;
        info.location = player.getLocation();
        info.health = player.getHealth();
        info.foodLevel = player.getFoodLevel();
        info.inventoryContents = player.getInventory().getContents();
        info.effects = player.getActivePotionEffects();
        if (info.groups.isEmpty()) {
            info.groups.clear();
            info.groups.add("member");
        }
        playerData.put(name, info);
        saveData();
    }

    public PlayerInfo getPlayerInfo(String name) {
        PlayerInfo info = playerData.getOrDefault(name, new PlayerInfo(0, null));
        if (info.groups.isEmpty()) info.groups.add("member");
        return info;
    }

    public boolean iseffects_enabled(Player player) {
        return getPlayerInfo(player.getName()).effects_enabled;
    }

    public void setParticle(Player player, Particle particle) {
        PlayerInfo info = getPlayerInfo(player.getName());
        info.particle = particle;
        saveData();
    }

    public Particle getParticle(Player player) {
        return getPlayerInfo(player.getName()).particle;
    }

    public void banPlayer(Player player, String reason) {
        context.getBanManager().banPlayer(player.getName(), -1, reason);
    }

    public boolean isBanned(Player player) {
        return getPlayerInfo(player.getName()).groups.contains("banned");
    }

    public static class PlayerInfo {
        public int login_count;
        public int sign_in_count;
        public Location location;
        public double health;
        public int foodLevel;
        public ItemStack[] inventoryContents;
        public @NotNull Collection<PotionEffect> effects;
        public long last_sign_in;
        public List<String> groups;
        public boolean effects_enabled;
        public Particle particle;

        public PlayerInfo(int login_count, Location location) {
            this.login_count = login_count;
            this.sign_in_count = 0;
            this.location = location;
            this.health = 20.0;
            this.foodLevel = 20;
            this.inventoryContents = new ItemStack[36];
            this.effects = new ArrayList<>();
            this.last_sign_in = 0L;
            this.groups = new ArrayList<>();
            this.effects_enabled = true;
            this.particle = Particle.FIREWORK;
        }

        public String getInventoryDescription() {
            if (inventoryContents == null) return "背包为空";
            StringBuilder desc = new StringBuilder();
            for (int i = 0; i < inventoryContents.length; i++) {
                ItemStack item = inventoryContents[i];
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    desc.append("槽 ").append(i + 1).append(": ")
                            .append(item.getAmount()).append("个 ")
                            .append(item.getType().name().toLowerCase().replace("_", " "))
                            .append(", ");
                }
            }
            return desc.length() > 0 ? desc.substring(0, desc.length() - 2) : "背包为空";
        }

        public String getEffectsDescription() {
            if (effects == null || effects.isEmpty()) return "无效果";
            return effects.stream()
                    .map(e -> e.getType().getName() + " (等级 " + (e.getAmplifier() + 1) + ", " + e.getDuration() / 20 + "秒)")
                    .collect(Collectors.joining(", "));
        }
    }

    public String analyzePlayer(Player player) {
        PlayerInfo info = getPlayerInfo(player.getName());
        return "玩家状态: " + info.health + "生命, " + info.foodLevel + "饥饿值 | 背包: " + info.getInventoryDescription() +
                " | 效果: " + info.getEffectsDescription();
    }

    public void setlast_sign_in(Player player, long time) {
        PlayerInfo info = getPlayerInfo(player.getName());
        info.last_sign_in = time;
        saveData();
    }

    public long getlast_sign_in(Player player) {
        return getPlayerInfo(player.getName()).last_sign_in;
    }
}