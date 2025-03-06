package com.darkpixel.utils;
import com.darkpixel.Global;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
public class PlayerData {
    private final Map<String, PlayerInfo> playerData = new HashMap<>();
    private final File file;
    private YamlConfiguration config;
    private final Global context;
    public PlayerData(Global context) {
        this.context = context;
        file = new File(context.getPlugin().getDataFolder(), "player.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                LogUtil.severe("创建 player.yml 失败: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        loadData();
    }
    private void loadData() {
        for (String key : config.getKeys(false)) {
            int loginCount = config.getInt(key + ".loginCount", 0);
            double x = config.getDouble(key + ".x", 0);
            double y = config.getDouble(key + ".y", 0);
            double z = config.getDouble(key + ".z", 0);
            String world = config.getString(key + ".world", "world");
            List<String> cheatTriggers = config.getStringList(key + ".cheatTriggers");
            PlayerInfo info = new PlayerInfo(loginCount, new Location(context.getPlugin().getServer().getWorld(world), x, y, z));
            info.cheatTriggers = cheatTriggers.isEmpty() ? new ArrayList<>() : cheatTriggers;
            playerData.put(key, info);
        }
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
        playerData.put(name, info);
        saveDataAsync();
    }
    public PlayerInfo getPlayerInfo(String name) {
        return playerData.getOrDefault(name, new PlayerInfo(0, null));
    }
    private void saveDataAsync() {
        for (Map.Entry<String, PlayerInfo> entry : playerData.entrySet()) {
            String name = entry.getKey();
            PlayerInfo info = entry.getValue();
            config.set(name + ".loginCount", info.loginCount);
            config.set(name + ".x", info.location.getX());
            config.set(name + ".y", info.location.getY());
            config.set(name + ".z", info.location.getZ());
            config.set(name + ".world", info.location.getWorld().getName());
            config.set(name + ".cheatTriggers", info.cheatTriggers);
        }
        FileUtil.saveAsync(config, file, context.getPlugin());
    }
    public static class PlayerInfo {
        public int loginCount;
        public Location location;
        public double health;
        public int foodLevel;
        public ItemStack[] inventoryContents;
        public Collection<PotionEffect> effects;
        public List<String> cheatTriggers; 
        public PlayerInfo(int loginCount, Location location) {
            this.loginCount = loginCount;
            this.location = location;
            this.health = 20.0;
            this.foodLevel = 20;
            this.inventoryContents = new ItemStack[36];
            this.effects = new ArrayList<>();
            this.cheatTriggers = new ArrayList<>();
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
        public String getCheatTriggersDescription() {
            return cheatTriggers.isEmpty() ? "No cheat triggers recorded" : String.join("\n", cheatTriggers);
        }
    }
    public String analyzePlayer(Player player) {
        PlayerInfo info = getPlayerInfo(player.getName());
        return "玩家状态: " + info.health + "生命, " + info.foodLevel + "饥饿值 | 背包: " + info.getInventoryDescription() +
                " | 效果: " + info.getEffectsDescription() + " | Cheat Triggers:\n" + info.getCheatTriggersDescription();
    }
}