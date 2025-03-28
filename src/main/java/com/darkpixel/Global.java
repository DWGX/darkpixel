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
import com.darkpixel.manager.BanManager;
import com.darkpixel.manager.CommandManager;
import com.darkpixel.manager.ConfigManager;
import com.darkpixel.npc.LobbyZombie;
import com.darkpixel.npc.NpcHandler;
import com.darkpixel.npc.RadioChestZombie;
import com.darkpixel.npc.SwitchChestZombie;
import com.darkpixel.rank.ChatListener;
import com.darkpixel.rank.RankCommands;
import com.darkpixel.rank.RankManager;
import com.darkpixel.rank.RankServer;
import com.darkpixel.utils.LoginMessageUtil;
import com.darkpixel.utils.MotdUtils;
import com.darkpixel.utils.PlayerData;
import com.darkpixel.utils.PlayerFreeze;
import com.darkpixel.utils.RankServerClient;
import com.darkpixel.utils.SitUtils;
import com.darkpixel.utils.WorldData;
import com.darkpixel.utils.effects.PlayerJoinEffects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
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
    private final RankServer rankServer;
    private final BanManager banManager;
    private final RankServerClient rankServerClient;
    private final ChatListener chatListener;
    private final boolean isRankServerRunning;

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
        this.banManager = new BanManager(this);
        this.rankServer = new RankServer(rankManager, playerData, this);
        this.chatListener = new ChatListener(playerData, rankManager);
        this.isRankServerRunning = checkPortAvailability();
        this.rankServerClient = new RankServerClient(configManager.getConfig().getString("rank_server_url", "http://localhost:" + configManager.getConfig().getInt("http_port", 25560)), this);
        new CommandManager(this);
        registerEvents();
        executor.submit(() -> configManager.reloadAllConfigsAsync());
        rankManager.startSyncTask();
        if (!isRankServerRunning) {
            executor.submit(() -> rankServerClient.syncAllPlayers());
        }
    }

    private boolean checkPortAvailability() {
        int port = configManager.getConfig().getInt("http_port", 25560);
        try (ServerSocketChannel serverSocket = ServerSocketChannel.open()) {
            serverSocket.bind(new InetSocketAddress(port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void registerEvents() {
        plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(new PlayerJoinEffects(playerData), plugin);
        plugin.getCommand("rank").setExecutor(new RankCommands(this));
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
    }

    public void shutdown() {
        HandlerList.unregisterAll(plugin);
        if (isRankServerRunning && rankServer != null) {
            rankServer.shutdown();
        }
        if (rankServerClient != null) {
            rankServerClient.shutdown();
        }
        if (!executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                    var pendingTasks = executor.shutdownNow();
                    plugin.getLogger().warning("线程池强制关闭，剩余 " + pendingTasks.size() + " 个未完成任务");
                } else {
                    plugin.getLogger().info("线程池已优雅关闭");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                plugin.getLogger().severe("线程池关闭被中断: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
        if (playerData != null) {
            playerData.saveData();
        }
        if (rankManager != null) {
            rankManager.saveAll();
        }
    }

    public void updateSitUtils() {
        if (sitUtils != null) {
            HandlerList.unregisterAll(sitUtils);
        }
        this.sitUtils = configManager.getConfig().getBoolean("sitting.enabled", true) ? new SitUtils(this) : null;
        if (sitUtils != null) {
            plugin.getServer().getPluginManager().registerEvents(sitUtils, plugin);
        }
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    public WorldData getWorldData() {
        return worldData;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public AiChatHandler getAiChat() {
        return aiChat;
    }

    public DashboardHandler getDashboard() {
        return dashboard;
    }

    public NpcHandler getNpcHandler() {
        return npcHandler;
    }

    public BringBackBlocking getBringBackBlocking() {
        return bringBackBlocking;
    }

    public LoginMessageUtil getLoginMessageUtil() {
        return loginMessageUtil;
    }

    public PlayerFreeze getPlayerFreeze() {
        return playerFreeze;
    }

    public MotdUtils getMotdUtils() {
        return motdUtils;
    }

    public SitUtils getSitUtils() {
        return sitUtils;
    }

    public YamlConfiguration getMinigameConfig() {
        return minigameConfig;
    }

    public YamlConfiguration getCommandConfig() {
        return commandConfig;
    }

    public AntiCheatHandler getAntiCheatHandler() {
        return antiCheatHandler;
    }

    public ServerSwitchChest getServerSwitchChest() {
        return serverSwitchChest;
    }

    public ServerRadioChest getServerRadioChest() {
        return serverRadioChest;
    }

    public SignInContainer getSignInContainer() {
        return signInContainer;
    }

    public RankServer getRankServer() {
        return rankServer;
    }

    public BanManager getBanManager() {
        return banManager;
    }

    public RankServerClient getRankServerClient() {
        return rankServerClient;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }

    public boolean isRankServerRunning() {
        return isRankServerRunning;
    }

    public YamlConfiguration getConfig() {
        return configManager.getConfig();
    }
}