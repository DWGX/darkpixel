package com.darkpixel.manager;
import com.darkpixel.utils.FileUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;
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
        initDefaultConfig();
    }
    private void initDefaultConfig() {
        YamlConfiguration config = configs.get("config.yml");
        config.addDefault("api_key", "sk-c4a51bdeab1f4a53805ecac6c8bf53f6");
        config.addDefault("ai_name", "dwgx");
        config.addDefault("available_models", Arrays.asList("deepseek-chat", "deepseek-reasoner", "deepseek-coder", "deepseek-pro"));
        config.addDefault("ai_whitelist", new ArrayList<String>());
        config.addDefault("player_message_limits", new HashMap<String, Integer>());
        config.addDefault("ai_public_prompt", "你是一个Minecraft 1.21.4服务器AI助手，名字叫“{ai_name}”。根据玩家输入，提供自然友好的文本回复，不生成命令（以/开头）。若无法理解，回复“{ai_name}不太明白，能再解释一下吗？”。可以根据玩家状态和历史对话自由发挥，提供建议或创意。\n玩家状态: {player_context}\n历史摘要: {history}\n当前输入: {message}");
        config.addDefault("ai_admin_prompt", "你是一个Minecraft 1.21.4服务器AI助手，名字叫“{ai_name}”。根据玩家输入和上下文，生成Minecraft命令（每行以 / 开头，多行以 \\n 分隔，附 \"AI: <简短回复>\"）。可以自由发挥，生成实用或创意的建筑、物品或效果。若无法理解，返回 \"/say {ai_name}无法理解，请明确需求 AI: 需要更多细节\"。参考相关命令，但不拘泥于模板。\n玩家状态: {player_context}\n历史摘要: {history}\n相关命令: {commands}\n背包: {inventory}\n效果: {effects}\n世界资源: {world_resources}\n当前输入: {message}");
        config.addDefault("ai_welcome_enabled", true);
        config.addDefault("ai_welcome_interval", 3600000L);
        config.addDefault("message_reset_interval", 72000L);
        config.addDefault("chat_history_max_size", 10);
        config.addDefault("blocking.enabled", true);
        config.addDefault("blocking.damage-reduce-percentage", 50);
        config.addDefault("blocking.reduce-only-entity-damage", true);
        config.addDefault("blocking.enable-vanilla-blocking", true);
        config.addDefault("npc_enabled", true);
        config.addDefault("dashboard_enabled", true);
        config.addDefault("dashboard_chat_limit", 5);
        config.addDefault("freeze.enabled", true);
        config.addDefault("freeze.persist_across_relog", false);
        config.addDefault("sitting.enabled", true);
        config.addDefault("sitting.allowSittingOnBlocks", true);
        config.addDefault("sitting.allowSittingOnPlayers", true);
        config.addDefault("sitting.blocked-worlds", new ArrayList<String>());
        config.addDefault("sitting.valid-blocks", Arrays.asList("stair", "slab", "step"));
        config.addDefault("sitting.player-permissions", new HashMap<String, Boolean>());
        config.options().copyDefaults(true);
        saveConfig("config.yml");
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
    public JavaPlugin getPlugin() { return plugin; }
    public synchronized void saveConfig(String configName) {
        YamlConfiguration config = configs.get(configName);
        File file = configFiles.get(configName);
        FileUtil.saveAsync(config, file, plugin).thenRun(() ->
                plugin.getLogger().info(configName + " 保存完成"));
    }
}