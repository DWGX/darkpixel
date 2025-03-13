package com.darkpixel.ai;

import com.darkpixel.manager.ConfigManager;
import com.darkpixel.utils.FileUtil;
import com.darkpixel.utils.LogUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class AiChatHistory {
    private final ConfigManager config;
    private final Map<String, List<String>> playerChatHistory = new ConcurrentHashMap<>();
    final Set<String> stoppedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final int maxHistorySize;

    public AiChatHistory(ConfigManager config) {
        this.config = config;
        this.maxHistorySize = config.getConfig().getInt("chat_history_max_size", 10);
        if (LogUtil.logger == null) {
            throw new IllegalStateException("LogUtil 未初始化，请在插件启动时调用 LogUtil.init()");
        }
    }

    public void loadChatHistoryAsync() {
        new BukkitRunnable() {
            @Override
            public void run() {
                File configFile = new File(config.getPlugin().getDataFolder(), "chat_history.yml");
                try {
                    YamlConfiguration yaml = FileUtil.loadOrCreate(configFile, config.getPlugin(), "chat_history.yml");
                    Map<String, Object> data = yaml.getValues(false);
                    lock.writeLock().lock();
                    try {
                        data.forEach((player, history) -> {
                            if (history instanceof List) {
                                playerChatHistory.put(player, new ArrayList<>((List<String>) history));
                            }
                        });
                        LogUtil.info("异步加载聊天历史完成，加载了 " + playerChatHistory.size() + " 个玩家的记录");
                    } finally {
                        lock.writeLock().unlock();
                    }
                } catch (Exception e) {
                    LogUtil.severe("加载 chat_history.yml 失败: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(config.getPlugin());
    }

    public void saveChatHistory() {
        File configFile = new File(config.getPlugin().getDataFolder(), "chat_history.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        lock.readLock().lock();
        try {
            playerChatHistory.forEach(yaml::set);
            FileUtil.saveAsync(yaml, configFile, config.getPlugin());
        } catch (Exception e) {
            LogUtil.severe("保存 chat_history.yml 失败: " + e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addMessage(String player, String message) {
        if (stoppedPlayers.contains(player)) return;
        lock.writeLock().lock();
        try {
            List<String> history = playerChatHistory.computeIfAbsent(player, k -> new ArrayList<>());
            synchronized (history) {
                history.add(message);
                if (history.size() > maxHistorySize) history.remove(0);
            }
            LogUtil.info("添加聊天记录: " + player + " -> " + message);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getHistory(String player) {
        lock.readLock().lock();
        try {
            return playerChatHistory.getOrDefault(player, new ArrayList<>()).stream().collect(Collectors.joining("\n"));
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getMessageContext(String player, String message) {
        String historyContext = getHistory(player);
        return "[" + player + "] " + message + (historyContext.isEmpty() ? "" : "\n历史记录:\n" + historyContext);
    }

    public void clearHistory(String player) {
        lock.writeLock().lock();
        try {
            playerChatHistory.remove(player);
            LogUtil.info("清除玩家 " + player + " 的聊天历史");
            saveChatHistory();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<String> getPlayerHistory(String player) {
        lock.readLock().lock();
        try {
            return new ArrayList<>(playerChatHistory.getOrDefault(player, new ArrayList<>()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void toggleHistory(String player, boolean stop) {
        if (stop) stoppedPlayers.add(player);
        else stoppedPlayers.remove(player);
        LogUtil.info("玩家 " + player + " 的历史记录功能已" + (stop ? "暂停" : "恢复"));
    }
}