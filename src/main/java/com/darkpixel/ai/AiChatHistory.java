package com.darkpixel.ai;

import com.darkpixel.manager.ConfigManager;
import com.darkpixel.utils.LogUtil;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AiChatHistory {
    private final ConfigManager config;
    private final Map<String, List<String>> playerChatHistory = new ConcurrentHashMap<>();
    final Set<String> stoppedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final int maxHistorySize;
    private Connection connection;

    public AiChatHistory(ConfigManager config) {
        this.config = config;
        this.maxHistorySize = config.getConfig().getInt("chat_history_max_size", 10);
        initDatabase();
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + config.getPlugin().getDataFolder() + "/chat_history.db");
            Statement stmt = connection.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_history (player TEXT, message TEXT, timestamp BIGINT)");
            stmt.close();
        } catch (SQLException e) {
            LogUtil.severe("Failed to initialize SQLite database: " + e.getMessage());
        }
    }

    public void loadChatHistoryAsync() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT player, message FROM chat_history ORDER BY timestamp DESC");
                    while (rs.next()) {
                        String player = rs.getString("player");
                        String message = rs.getString("message");
                        List<String> history = playerChatHistory.computeIfAbsent(player, k -> new ArrayList<>());
                        synchronized (history) {
                            history.add(message);
                            if (history.size() > maxHistorySize) history.remove(0);
                        }
                    }
                    rs.close();
                    stmt.close();
                } catch (SQLException e) {
                    LogUtil.severe("Failed to load chat history: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(config.getPlugin());
    }

    public void saveChatHistoryAsync() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    connection.setAutoCommit(false);
                    PreparedStatement pstmt = connection.prepareStatement("INSERT INTO chat_history (player, message, timestamp) VALUES (?, ?, ?)");
                    playerChatHistory.forEach((player, history) -> {
                        synchronized (history) {
                            for (String message : history) {
                                try {
                                    pstmt.setString(1, player);
                                    pstmt.setString(2, message);
                                    pstmt.setLong(3, System.currentTimeMillis());
                                    pstmt.addBatch();
                                } catch (SQLException e) {
                                    LogUtil.severe("Failed to save message for " + player + ": " + e.getMessage());
                                }
                            }
                        }
                    });
                    pstmt.executeBatch();
                    connection.commit();
                    pstmt.close();
                } catch (SQLException e) {
                    LogUtil.severe("Failed to save chat history: " + e.getMessage());
                } finally {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException e) {
                        LogUtil.severe("Failed to reset auto-commit: " + e.getMessage());
                    }
                }
            }
        }.runTaskAsynchronously(config.getPlugin());
    }

    public void addMessage(String player, String message) {
        if (stoppedPlayers.contains(player)) return;
        List<String> history = playerChatHistory.computeIfAbsent(player, k -> new ArrayList<>());
        synchronized (history) {
            history.add(message);
            if (history.size() > maxHistorySize) history.remove(0);
        }
        LogUtil.info("Added chat record: " + player + " -> " + message);
    }

    public String getHistory(String player) {
        List<String> history = playerChatHistory.getOrDefault(player, new ArrayList<>());
        synchronized (history) {
            return String.join("\n", history);
        }
    }

    public String getHistorySummary(String player) {
        List<String> history = playerChatHistory.getOrDefault(player, new ArrayList<>());
        synchronized (history) {
            if (history.isEmpty()) return "";
            if (history.size() <= 3) return String.join("\n", history);
            List<String> keywords = new ArrayList<>();
            for (String msg : history) {
                if (msg.contains("背包")) keywords.add("提到背包");
                else if (msg.contains("作弊")) keywords.add("提到作弊");
                else if (msg.contains("位置")) keywords.add("提到位置");
            }
            return keywords.isEmpty() ? "近期对话无明显主题" : "近期对话主题: " + String.join(", ", keywords);
        }
    }

    public void clearHistory(String player) {
        playerChatHistory.remove(player);
        try {
            PreparedStatement pstmt = connection.prepareStatement("DELETE FROM chat_history WHERE player = ?");
            pstmt.setString(1, player);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            LogUtil.severe("Failed to clear history for " + player + ": " + e.getMessage());
        }
        LogUtil.info("Cleared chat history for " + player);
    }

    public List<String> getPlayerHistory(String player) {
        return new ArrayList<>(playerChatHistory.getOrDefault(player, new ArrayList<>()));
    }

    public void toggleHistory(String player, boolean stop) {
        if (stop) stoppedPlayers.add(player);
        else stoppedPlayers.remove(player);
        LogUtil.info("History recording for " + player + " has been " + (stop ? "paused" : "resumed"));
    }

    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            LogUtil.severe("Failed to close SQLite connection: " + e.getMessage());
        }
    }
}