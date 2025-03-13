package com.darkpixel.manager;

import com.darkpixel.utils.FileUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> configs = new HashMap<>();
    private final Map<String, File> configFiles = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        String[] configNames = {"config.yml", "minigame.yml", "commands.yml", "chat_history.yml",
                "darkac.yml", "freeze_data.yml", "player.yml", "world_data.yml"};
        for (String configName : configNames) {
            File file = new File(plugin.getDataFolder(), configName);
            configFiles.put(configName, file);
            configs.put(configName, FileUtil.loadOrCreate(file, plugin, configName));
        }
    }

    public synchronized void reloadAllConfigs() {
        for (String configName : configFiles.keySet()) {
            configs.put(configName, YamlConfiguration.loadConfiguration(configFiles.get(configName)));
            plugin.getLogger().info("已重新加载配置文件: " + configName);
        }
    }

    public YamlConfiguration getConfig(String configName) {
        return configs.getOrDefault(configName, new YamlConfiguration());
    }

    public YamlConfiguration getConfig() {
        return getConfig("config.yml");
    }

    public YamlConfiguration getMinigameConfig() { return getConfig("minigame.yml"); }
    public YamlConfiguration getCommandConfig() { return getConfig("commands.yml"); }
    public YamlConfiguration getChatHistoryConfig() { return getConfig("chat_history.yml"); }
    public YamlConfiguration getDarkAcConfig() { return getConfig("darkac.yml"); }
    public YamlConfiguration getFreezeDataConfig() { return getConfig("freeze_data.yml"); }
    public YamlConfiguration getPlayerConfig() { return getConfig("player.yml"); }
    public YamlConfiguration getWorldDataConfig() { return getConfig("world_data.yml"); }
    public String getApiKey() { return getConfig().getString("api_key", ""); }
    public String getApiProvider() { return getConfig().getString("api_provider", "deepseek"); }
    public String getAiName() { return getConfig().getString("ai_name", "AI"); }
    public List<String> getAvailableModels() { return getConfig().getStringList("available_models"); }
    public String getSystemPrompt() { return getConfig().getString("ai_public_prompt", "").replace("{ai_name}", getAiName()); }
    public String getAdminPrompt() { return getConfig().getString("ai_admin_prompt", "").replace("{ai_name}", getAiName()); }
    public List<String> getWhitelist() { return getConfig().getStringList("ai_whitelist"); }
    public Map<String, Integer> getMessageLimits() {
        Map<String, Integer> limits = new HashMap<>();
        if (getConfig().getConfigurationSection("player_message_limits") != null) {
            getConfig().getConfigurationSection("player_message_limits").getKeys(false)
                    .forEach(k -> limits.put(k, getConfig().getInt("player_message_limits." + k, 5)));
        }
        return limits;
    }

    public List<String> getNpcLocations() { return getConfig().getStringList("npc_locations"); }
    public long getAiWelcomeInterval() { return getConfig().getLong("ai_welcome_interval", 3600000L); }
    public boolean isVanillaBlockingEnabled() { return getConfig().getBoolean("blocking.enable-vanilla-blocking", false); }
    public boolean isSittingEnabled() { return getConfig().getBoolean("sitting.enabled", true); }
    public boolean isSittingOnBlocksAllowed() { return getConfig().getBoolean("sitting.allowSittingOnBlocks", true); }
    public boolean isSittingOnPlayersAllowed() { return getConfig().getBoolean("sitting.allowSittingOnPlayers", true); }
    public List<String> getSittingBlockedWorlds() { return getConfig().getStringList("sitting.blocked-worlds"); }
    public List<String> getValidSittingBlocks() { return getConfig().getStringList("sitting.valid-blocks"); }
    public boolean canBeSatOn(Player player) {
        return getConfig().getBoolean("sitting.player-permissions." + player.getUniqueId().toString(), true);
    }

    public synchronized void toggleSittingPermission(Player player) {
        String path = "sitting.player-permissions." + player.getUniqueId().toString();
        boolean current = getConfig().getBoolean(path, true);
        getConfig().set(path, !current);
        saveConfig("config.yml");
    }

    public boolean getSittingPermission(Player player) {
        return getConfig().getBoolean("sitting.player-permissions." + player.getUniqueId().toString(), true);
    }

    public synchronized void saveWhitelist(Set<String> whitelist) {
        getConfig().set("ai_whitelist", new ArrayList<>(whitelist));
        saveConfig("config.yml");
    }

    public synchronized void saveMessageLimits(Map<String, Integer> limits) {
        getConfig().set("player_message_limits", null);
        limits.forEach((k, v) -> getConfig().set("player_message_limits." + k, v));
        saveConfig("config.yml");
    }

    public synchronized void saveNpcLocations(List<String> locations) {
        getConfig().set("npc_locations", locations);
        saveConfig("config.yml");
    }

    public double getSittingHeightOffsetBlocks() {
        return getConfig().getDouble("sitting.height-offset.blocks", 0.5);
    }

    public double getSittingHeightOffsetPlayers() {
        return getConfig().getDouble("sitting.height-offset.players", 0.2);
    }

    public JavaPlugin getPlugin() { return plugin; }

    public synchronized void saveConfig(String configName) {
        YamlConfiguration config = configs.get(configName);
        File file = configFiles.get(configName);
        FileUtil.saveAsync(config, file, plugin);
    }
}