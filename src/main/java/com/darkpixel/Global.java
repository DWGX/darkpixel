package com.darkpixel;
import com.darkpixel.ai.AiChatEvents;
import com.darkpixel.ai.AiChatHandler;
import com.darkpixel.ai.AiChatHandlerImpl;
import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.combat.bringBackBlocking.BringBackBlocking;
import com.darkpixel.gui.DashboardHandler;
import com.darkpixel.manager.CommandManager;
import com.darkpixel.manager.ConfigManager;
import com.darkpixel.utils.*;
import com.darkpixel.npc.NpcHandler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.*;
public class Global {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final AiChatHandler aiChat;
    private final DashboardHandler dashboard;
    private final NpcHandler npcHandler;
    private final BringBackBlocking bringBackBlocking;
    private final LoginMessageUtil loginMessageUtil;
    private final PlayerData playerData;
    private final WorldData worldData;
    private final PlayerFreeze playerFreeze;
    private final MotdUtils motdUtils;
    private final SitUtils sitUtils;
    private final YamlConfiguration minigameConfig;
    private final YamlConfiguration commandConfig;
    private final AntiCheatHandler antiCheatHandler;
    public static final ExecutorService executor = new ThreadPoolExecutor(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            Runtime.getRuntime().availableProcessors() * 2,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    public Global(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = new ConfigManager(plugin);
        this.playerData = new PlayerData(this);
        this.worldData = new WorldData(this);
        this.minigameConfig = configManager.getMinigameConfig();
        this.commandConfig = configManager.getCommandConfig();
        this.aiChat = initAiChat();
        this.bringBackBlocking = initBringBackBlocking();
        this.dashboard = initDashboard();
        this.npcHandler = initNpcHandler();
        this.loginMessageUtil = new LoginMessageUtil(this);
        this.playerFreeze = initPlayerFreeze();
        this.motdUtils = new MotdUtils((Main) plugin);
        this.sitUtils = initSitUtils();
        this.antiCheatHandler = new AntiCheatHandler(this);
        new CommandManager(this);
        registerEvents();
        configManager.reloadAllConfigs();
    }
    private AiChatHandler initAiChat() {
        return new AiChatHandlerImpl(configManager, this);
    }
    private BringBackBlocking initBringBackBlocking() {
        return configManager.getConfig().getBoolean("blocking.enabled", true) ? new BringBackBlocking(this) : null;
    }
    private DashboardHandler initDashboard() {
        return configManager.getConfig().getBoolean("dashboard_enabled", true) ?
                new DashboardHandler(plugin, aiChat, configManager, this) : null;
    }
    private NpcHandler initNpcHandler() {
        return configManager.getConfig().getBoolean("npc_enabled", true) ? new NpcHandler(configManager, dashboard) : null;
    }
    private PlayerFreeze initPlayerFreeze() {
        return configManager.getConfig().getBoolean("freeze.enabled", true) ? new PlayerFreeze(this) : null;
    }
    private SitUtils initSitUtils() {
        return configManager.getConfig().getBoolean("sitting.enabled", true) ? new SitUtils(this) : null;
    }
    private void registerEvents() {
        if (bringBackBlocking != null) plugin.getServer().getPluginManager().registerEvents(bringBackBlocking, plugin);
        plugin.getServer().getPluginManager().registerEvents(new AiChatEvents(aiChat), plugin);
        if (npcHandler != null) plugin.getServer().getPluginManager().registerEvents(npcHandler, plugin);
        plugin.getServer().getPluginManager().registerEvents(loginMessageUtil, plugin);
        if (playerFreeze != null) plugin.getServer().getPluginManager().registerEvents(playerFreeze, plugin);
        plugin.getServer().getPluginManager().registerEvents(motdUtils, plugin);
        if (dashboard != null) plugin.getServer().getPluginManager().registerEvents(dashboard, plugin);
        if (sitUtils != null) plugin.getServer().getPluginManager().registerEvents(sitUtils, plugin);
        plugin.getServer().getPluginManager().registerEvents(antiCheatHandler, plugin);
    }
    public JavaPlugin getPlugin() { return plugin; }
    public ConfigManager getConfigManager() { return configManager; }
    public YamlConfiguration getConfig() { return configManager.getConfig(); }
    public YamlConfiguration getMinigameConfig() { return minigameConfig; }
    public YamlConfiguration getCommandConfig() { return commandConfig; }
    public AiChatHandler getAiChat() { return aiChat; }
    public BringBackBlocking getBringBackBlocking() { return bringBackBlocking; }
    public DashboardHandler getDashboard() { return dashboard; }
    public NpcHandler getNpcHandler() { return npcHandler; }
    public LoginMessageUtil getLoginMessageUtil() { return loginMessageUtil; }
    public PlayerData getPlayerData() { return playerData; }
    public WorldData getWorldData() { return worldData; }
    public PlayerFreeze getPlayerFreeze() { return playerFreeze; }
    public MotdUtils getMotdUtils() { return motdUtils; }
    public SitUtils getSitUtils() { return sitUtils; }
    public AntiCheatHandler getAntiCheatHandler() { return antiCheatHandler; }
}