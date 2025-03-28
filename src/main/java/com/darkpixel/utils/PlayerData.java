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
                int loginCount = rs.getInt("loginCount");
                int signInCount = rs.getInt("signInCount");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                String world = rs.getString("world");
                long lastSignIn = rs.getLong("lastSignIn");
                boolean effects_enabled = rs.getBoolean("effects_enabled");
                String particle = rs.getString("particle");
                PlayerInfo info = new PlayerInfo(loginCount, new Location(context.getPlugin().getServer().getWorld(world), x, y, z));
                info.signInCount = signInCount;
                info.lastSignIn = lastSignIn;
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
            int loginCount = config.getInt(key + ".loginCount", 0);
            int signInCount = config.getInt(key + ".signInCount", 0);
            double x = config.getDouble(key + ".x", 0);
            double y = config.getDouble(key + ".y", 0);
            double z = config.getDouble(key + ".z", 0);
            String world = config.getString(key + ".world", "world");
            long lastSignIn = config.getLong(key + ".lastSignIn", 0L);
            boolean effects_enabled = config.getBoolean(key + ".effects_enabled", true);
            String particle = config.getString(key + ".particle", "FIREWORK");
            PlayerInfo info = new PlayerInfo(loginCount, new Location(context.getPlugin().getServer().getWorld(world), x, y, z));
            info.signInCount = signInCount;
            info.lastSignIn = lastSignIn;
            info.effects_enabled = effects_enabled;
            info.particle = Particle.valueOf(particle);
            if (info.groups.isEmpty()) info.groups.add("member");
            playerData.put(key, info);
        }
    }

    public void saveData() {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // 开启事务
            for (Map.Entry<String, PlayerInfo> entry : playerData.entrySet()) {
                String name = entry.getKey();
                PlayerInfo info = entry.getValue();
                Player player = context.getPlugin().getServer().getPlayer(name);
                String uuid = player != null ? player.getUniqueId().toString() :
                        context.getPlugin().getServer().getOfflinePlayer(name).getUniqueId().toString();

                // 保存玩家基本信息
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO players (uuid, name, loginCount, signInCount, x, y, z, world, lastSignIn, effects_enabled, particle) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                                "name = ?, loginCount = ?, signInCount = ?, x = ?, y = ?, z = ?, world = ?, lastSignIn = ?, effects_enabled = ?, particle = ?")) {
                    ps.setString(1, uuid);
                    ps.setString(2, name);
                    ps.setInt(3, info.loginCount);
                    ps.setInt(4, info.signInCount);
                    ps.setDouble(5, info.location.getX());
                    ps.setDouble(6, info.location.getY());
                    ps.setDouble(7, info.location.getZ());
                    ps.setString(8, info.location.getWorld().getName());
                    ps.setLong(9, info.lastSignIn);
                    ps.setBoolean(10, info.effects_enabled);
                    ps.setString(11, info.particle.name());
                    ps.setString(12, name);
                    ps.setInt(13, info.loginCount);
                    ps.setInt(14, info.signInCount);
                    ps.setDouble(15, info.location.getX());
                    ps.setDouble(16, info.location.getY());
                    ps.setDouble(17, info.location.getZ());
                    ps.setString(18, info.location.getWorld().getName());
                    ps.setLong(19, info.lastSignIn);
                    ps.setBoolean(20, info.effects_enabled);
                    ps.setString(21, info.particle.name());
                    ps.executeUpdate();
                }

                // 删除旧的组记录
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_groups WHERE uuid = ?")) {
                    ps.setString(1, uuid);
                    ps.executeUpdate();
                }

                // 使用 Set 去重并插入组记录
                Set<String> uniqueGroups = new HashSet<>(info.groups); // 去重
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO player_groups (uuid, player_name, group_name) VALUES (?, ?, ?)")) {
                    for (String group : uniqueGroups) {
                        ps.setString(1, uuid);
                        ps.setString(2, name);
                        ps.setString(3, group);
                        ps.addBatch(); // 使用批量插入
                    }
                    ps.executeBatch(); // 执行批量插入
                }
            }
            conn.commit(); // 提交事务
        } catch (SQLException e) {
            context.getPlugin().getLogger().severe("保存玩家数据失败: " + e.getMessage());
            try (Connection conn = getConnection()) {
                conn.rollback(); // 回滚事务
            } catch (SQLException rollbackEx) {
                context.getPlugin().getLogger().severe("回滚事务失败: " + rollbackEx.getMessage());
            }
            saveDataAsync(); // 失败时保存到 YAML
        }
    }

    private void saveDataAsync() {
        for (Map.Entry<String, PlayerInfo> entry : playerData.entrySet()) {
            String name = entry.getKey();
            PlayerInfo info = entry.getValue();
            config.set(name + ".loginCount", info.loginCount);
            config.set(name + ".signInCount", info.signInCount);
            config.set(name + ".x", info.location.getX());
            config.set(name + ".y", info.location.getY());
            config.set(name + ".z", info.location.getZ());
            config.set(name + ".world", info.location.getWorld().getName());
            config.set(name + ".lastSignIn", info.lastSignIn);
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

    public int getSignInCount(Player player) {
        return getPlayerInfo(player.getName()).signInCount;
    }

    public void setSignInCount(Player player, int count) {
        PlayerInfo info = getPlayerInfo(player.getName());
        info.signInCount = count;
        saveData();
    }

    public void updatePlayer(Player player) {
        String name = player.getName();
        PlayerInfo info = playerData.getOrDefault(name, new PlayerInfo(0, player.getLocation()));
        info.loginCount++;
        info.location = player.getLocation();
        info.health = player.getHealth();
        info.foodLevel = player.getFoodLevel();
        info.inventoryContents = player.getInventory().getContents();
        info.effects = player.getActivePotionEffects();
        if (info.groups.isEmpty()) {
            info.groups.clear(); // 清空后再添加，避免重复
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
        public int loginCount;
        public int signInCount;
        public Location location;
        public double health;
        public int foodLevel;
        public ItemStack[] inventoryContents;
        public @NotNull Collection<PotionEffect> effects;
        public long lastSignIn;
        public List<String> groups;
        public boolean effects_enabled;
        public Particle particle;

        public PlayerInfo(int loginCount, Location location) {
            this.loginCount = loginCount;
            this.signInCount = 0;
            this.location = location;
            this.health = 20.0;
            this.foodLevel = 20;
            this.inventoryContents = new ItemStack[36];
            this.effects = new ArrayList<PotionEffect>();
            this.lastSignIn = 0L;
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

    public void setLastSignIn(Player player, long time) {
        PlayerInfo info = getPlayerInfo(player.getName());
        info.lastSignIn = time;
        saveData();
    }

    public long getLastSignIn(Player player) {
        return getPlayerInfo(player.getName()).lastSignIn;
    }
}