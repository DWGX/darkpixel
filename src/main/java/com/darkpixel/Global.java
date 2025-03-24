package com.darkpixel;

import com.darkpixel.ai.AiChatEvents;
import com.darkpixel.ai.AiChatHandler;
import com.darkpixel.ai.AiChatHandlerImpl;
import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.combat.bringBackBlocking.BringBackBlocking;
import com.darkpixel.gui.DashboardHandler;
import com.darkpixel.gui.ServerSwitchChest;
import com.darkpixel.gui.ServerRadioChest;
import com.darkpixel.gui.SignInContainer;
import com.darkpixel.manager.CommandManager;
import com.darkpixel.manager.ConfigManager;
import com.darkpixel.npc.LobbyZombie;
import com.darkpixel.npc.NpcHandler;
import com.darkpixel.npc.RadioChestZombie;
import com.darkpixel.npc.SwitchChestZombie;
import com.darkpixel.rank.*;
import com.darkpixel.utils.*;
import com.darkpixel.utils.effects.PlayerJoinEffects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Global {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerData playerData;
    private final WorldData worldData;
    private final RankManager rankManager;
    private final AiChatHandler aiChat;
    private final DashboardHandler dashboard;
    private final NpcHandler npcHandler;
    private final BringBackBlocking bringBackBlocking;
    private final LoginMessageUtil loginMessageUtil;
    private final PlayerFreeze playerFreeze;
    private final MotdUtils motdUtils;
    private SitUtils sitUtils;
    private final YamlConfiguration minigameConfig;
    private final YamlConfiguration commandConfig;
    private final AntiCheatHandler antiCheatHandler;
    private final ServerSwitchChest serverSwitchChest;
    private final ServerRadioChest serverRadioChest;
    private final SwitchChestZombie switchChestZombie;
    private final RadioChestZombie radioChestZombie;
    private final LobbyZombie lobbyZombie;
    private final SignInContainer signInContainer;
    private final RankServer rankServer; // 新增 RankServer 实例

    public static final ExecutorService executor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public Global(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = new ConfigManager(plugin);
        this.playerData = new PlayerData(this);
        this.worldData = new WorldData(this);
        this.minigameConfig = configManager.getMinigameConfig();
        this.commandConfig = configManager.getCommandConfig();
        this.rankManager = new RankManager(this);
        this.aiChat = new AiChatHandlerImpl(configManager, this);
        this.bringBackBlocking = configManager.getConfig().getBoolean("blocking.enabled", true) ? new BringBackBlocking(this) : null;
        this.dashboard = configManager.getConfig().getBoolean("dashboard_enabled", true) ? new DashboardHandler(plugin, aiChat, configManager, this) : null;
        this.serverSwitchChest = new ServerSwitchChest(this);
        this.serverRadioChest = new ServerRadioChest(this);
        this.npcHandler = new NpcHandler(configManager, dashboard, serverSwitchChest, serverRadioChest);
        this.switchChestZombie = new SwitchChestZombie(serverSwitchChest);
        this.radioChestZombie = new RadioChestZombie(serverRadioChest);
        this.lobbyZombie = new LobbyZombie(dashboard);
        this.loginMessageUtil = new LoginMessageUtil(this);
        this.playerFreeze = configManager.getConfig().getBoolean("freeze.enabled", true) ? new PlayerFreeze(this) : null;
        this.motdUtils = new MotdUtils((Main) plugin);
        this.sitUtils = configManager.getConfig().getBoolean("sitting.enabled", true) ? new SitUtils(this) : null;
        this.antiCheatHandler = new AntiCheatHandler(this);
        this.signInContainer = new SignInContainer(this, rankManager);
        this.rankServer = new RankServer(rankManager, playerData); // 初始化 RankServer

        new CommandManager(this);
        plugin.getServer().getPluginManager().registerEvents(new ChatListener(playerData, rankManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PlayerJoinEffects(playerData), plugin);
        plugin.getCommand("toggleeffects").setExecutor(new ToggleEffectsCommand(playerData));
        plugin.getCommand("rank").setExecutor(new RankCommands(rankManager));
        if (bringBackBlocking != null && configManager.getConfig().getBoolean("enable_bring_back_blocking", true)) {
            plugin.getServer().getPluginManager().registerEvents(bringBackBlocking, plugin);
        }
        plugin.getServer().getPluginManager().registerEvents(new AiChatEvents(aiChat), plugin);
        plugin.getServer().getPluginManager().registerEvents(npcHandler, plugin);
        plugin.getServer().getPluginManager().registerEvents(switchChestZombie, plugin);
        plugin.getServer().getPluginManager().registerEvents(radioChestZombie, plugin);
        plugin.getServer().getPluginManager().registerEvents(lobbyZombie, plugin);
        plugin.getServer().getPluginManager().registerEvents(loginMessageUtil, plugin);
        if (playerFreeze != null) plugin.getServer().getPluginManager().registerEvents(playerFreeze, plugin);
        plugin.getServer().getPluginManager().registerEvents(motdUtils, plugin);
        if (dashboard != null) plugin.getServer().getPluginManager().registerEvents(dashboard, plugin);
        if (sitUtils != null) plugin.getServer().getPluginManager().registerEvents(sitUtils, plugin);
        plugin.getServer().getPluginManager().registerEvents(antiCheatHandler, plugin);
        plugin.getServer().getPluginManager().registerEvents(serverSwitchChest, plugin);
        plugin.getServer().getPluginManager().registerEvents(serverRadioChest, plugin);
        Global.executor.submit(() -> configManager.reloadAllConfigsAsync());
    }

    public void updateSitUtils() {
        boolean enabled = configManager.getConfig().getBoolean("sitting.enabled", true);
        if (enabled && sitUtils == null) {
            sitUtils = new SitUtils(this);
            plugin.getServer().getPluginManager().registerEvents(sitUtils, plugin);
        } else if (!enabled && sitUtils != null) {
            sitUtils = null;
        }
    }

    // Getter 方法
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
    public ServerSwitchChest getServerSwitchChest() { return serverSwitchChest; }
    public ServerRadioChest getServerRadioChest() { return serverRadioChest; }
    public RankManager getRankManager() { return rankManager; }
    public SignInContainer getSignInContainer() { return signInContainer; }
    public RankServer getRankServer() { return rankServer; } // 新增 RankServer getter
}