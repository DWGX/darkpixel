package com.darkpixel.ai;

import com.darkpixel.Global;
import com.darkpixel.manager.ConfigManager;
import com.darkpixel.utils.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class AiChatHandlerImpl implements AiChatHandler {
    private final ConfigManager config;
    private final ApiClient api;
    final AiChatConfig chatConfig;
    final AiChatHistory chatHistory;
    private final Global context;
    private String aiName;
    private final Map<String, String> playerModels = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private List<String> COMMAND_WHITELIST;
    private Map<String, String> commandCache;
    private final Map<String, String> intentCache;

    public AiChatHandlerImpl(ConfigManager config, Global context) {
        this.config = config;
        this.context = context;
        this.aiName = config.getAiName();
        this.api = new ApiClient(config, context);
        this.chatConfig = new AiChatConfig(config);
        this.chatHistory = new AiChatHistory(config);
        this.COMMAND_WHITELIST = config.getConfig().getStringList("command_whitelist");
        this.commandCache = buildCommandCache();
        this.intentCache = new ConcurrentHashMap<>();
        startMessageReset();
    }

    public void reloadConfig() {
        this.aiName = config.getAiName();
        this.COMMAND_WHITELIST = config.getConfig().getStringList("command_whitelist");
        this.commandCache = buildCommandCache();
        chatConfig.loadConfig();
        chatHistory.loadChatHistoryAsync();
    }

    private Map<String, String> buildCommandCache() {
        Map<String, String> cache = new HashMap<>();
        YamlConfiguration cmdConfig = context.getCommandConfig();
        if (cmdConfig == null || !cmdConfig.contains("commands")) return cache;
        cmdConfig.getConfigurationSection("commands").getKeys(false).forEach(key -> {
            String format = cmdConfig.getString("commands." + key + ".format");
            String desc = cmdConfig.getString("commands." + key + ".description");
            cache.put(key, format + " - " + desc);
        });
        return cache;
    }

    private String cacheIntent(String message, String intent) {
        intentCache.put(message, intent);
        return intent;
    }

    private String getHistorySummary(String player) {
        List<String> history = chatHistory.getPlayerHistory(player);
        if (history.isEmpty()) return "无历史记录";
        StringBuilder summary = new StringBuilder();
        int maxSummaryLines = 3;
        for (int i = Math.max(0, history.size() - maxSummaryLines); i < history.size(); i++) {
            String msg = history.get(i);
            summary.append(msg.length() > 50 ? msg.substring(0, 50) + "..." : msg).append("\n");
        }
        return summary.toString();
    }

    @Override
    public void init(ConfigManager config) {
        Global.executor.submit(() -> {
            chatConfig.loadConfig();
            chatHistory.loadChatHistoryAsync();
        });
    }

    @Override
    public String getPlayerModel(String player) {
        return playerModels.getOrDefault(player, "deepseek-chat");
    }

    @Override
    public void setPlayerModel(String player, String model) {
        if (config.getAvailableModels().contains(model)) {
            playerModels.put(player, model);
        }
    }

    @Override
    public boolean canSendMessage(String player, boolean isOp) {
        return chatConfig.canSendMessage(player, isOp);
    }

    @Override
    public void sendMessage(Player player, String message, boolean isPublic) {
        sendMessage(player, message, isPublic, null);
    }

    @Override
    public void sendMessage(Player player, String message, boolean isPublic, Consumer<String> callback) {
        String name = player.getName();
        if (!canSendMessage(name, player.isOp())) {
            String resp = "§c每小时限制 " + getPlayerMessageLimit(name) + " 条消息，请稍后再试~";
            Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage(resp));
            if (callback != null) callback.accept(resp);
            return;
        }
        if (isRateLimited(name)) {
            String resp = "§c别急，" + aiName + " 还在思考中，请稍等一秒~";
            Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage(resp));
            if (callback != null) callback.accept(resp);
            return;
        }
        lastRequestTime.put(name, System.currentTimeMillis());
        PlayerData.PlayerInfo playerInfo = context.getPlayerData().getPlayerInfo(name);
        String prompt = buildPromptForPlayer(player, message, playerInfo);
        executeChatRequest(player, prompt, message, isPublic, callback);
    }

    private String buildPromptForPlayer(Player player, String message, PlayerData.PlayerInfo playerInfo) {
        String playerContext = buildPlayerContext(player, playerInfo);
        String inventory = playerInfo.getInventoryDescription();
        String effects = playerInfo.getEffectsDescription();
        String worldResources = context.getWorldData().analyzeWorld(player.getWorld());
        String rank = context.getRankManager().getRank(player);
        String score = String.valueOf(context.getRankManager().getScore(player));
        chatHistory.addMessage(player.getName(), "用户: " + message);
        return buildPrompt(player.getName(), message, playerContext + ", Rank: " + rank + ", 分数: " + score, inventory, effects, worldResources, false);
    }

    @Override
    public void sendAdminBroadcast(Player player, String message) {
        String name = player.getName();
        if (!player.hasPermission("darkpixel.admin")) {
            Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage("§c只有管理员可以使用 /aichat adm 命令！"));
            return;
        }
        lock.lock();
        try {
            chatHistory.addMessage(name, "管理员: " + message);
            PlayerData.PlayerInfo playerInfo = context.getPlayerData().getPlayerInfo(name);
            String playerContext = buildPlayerContext(player, playerInfo);
            String inventory = playerInfo.getInventoryDescription();
            String effects = playerInfo.getEffectsDescription();
            String worldResources = context.getWorldData().analyzeWorld(player.getWorld());
            String prompt = buildPrompt(name, message, playerContext, inventory, effects, worldResources, true);
            executeAdminRequest(player, prompt, message);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setPlayerMessageLimit(String player, int limit) {
        chatConfig.setPlayerMessageLimit(player, limit);
    }

    @Override
    public int getPlayerMessageLimit(String player) {
        return chatConfig.getPlayerMessageLimit(player);
    }

    @Override
    public void handleAdminCommand(Player player, String subCommand, String message) {
        boolean isOp = player.hasPermission("darkpixel.admin");
        String[] args = message.split(" ");
        String name = player.getName();
        if (!isOp) {
            Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage("§c无权限，请联系管理员！"));
            return;
        }
        switch (subCommand) {
            case "mode": if (args.length == 1) setPlayerModel(name, args[0]); break;
            case "setmodel": if (args.length == 2) setPlayerModel(args[0], args[1]); break;
            case "whitelist": if (args.length == 1) chatConfig.getWhitelist().add(args[0]); break;
            case "unwhitelist": if (args.length == 1) chatConfig.getWhitelist().remove(args[0]); break;
            case "setlimit": if (args.length == 2) setPlayerMessageLimit(args[0], Integer.parseInt(args[1])); break;
            case "addlimit": if (args.length == 2 && args[0].equalsIgnoreCase("dashboard")) context.getDashboard().addDashboardChatLimit(Integer.parseInt(args[1])); break;
            case "history":
                if (args.length == 2) {
                    String action = args[0].toLowerCase();
                    String target = args[1];
                    switch (action) {
                        case "clear": chatHistory.clearHistory(target); break;
                        case "open": chatHistory.getPlayerHistory(target).forEach(player::sendMessage); break;
                        case "stop": chatHistory.toggleHistory(target, !chatHistory.stoppedPlayers.contains(target)); break;
                    }
                }
                break;
            case "adm": sendAdminBroadcast(player, message); break;
            default: Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage("§c未知的管理命令"));
        }
    }

    private boolean isRateLimited(String name) {
        Long lastTime = lastRequestTime.get(name);
        return lastTime != null && (System.currentTimeMillis() - lastTime) < 1000;
    }

    private String buildPlayerContext(Player player, PlayerData.PlayerInfo info) {
        return "玩家名: " + player.getName() + ", 位置: " + player.getLocation().getBlockX() + "," +
                player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ() + ", 世界: " +
                player.getWorld().getName() + ", 登录次数: " + (info != null ? info.loginCount : 0) +
                ", 生命值: " + player.getHealth() + ", 饥饿值: " + player.getFoodLevel() +
                ", 是否飞行: " + (player.isFlying() ? "是" : "否");
    }

    private String buildPrompt(String name, String message, String playerContext, String inventory, String effects, String worldResources, boolean isAdmin) {
        String historySummary = getHistorySummary(name);
        String aiName = config.getAiName();

        if (isAdmin) {
            return config.getAdminPrompt()
                    .replace("{ai_name}", aiName)
                    .replace("{player_context}", playerContext)
                    .replace("{history}", historySummary)
                    .replace("{inventory}", inventory)
                    .replace("{effects}", effects)
                    .replace("{world_resources}", worldResources)
                    .replace("{message}", message);
        } else {
            return config.getSystemPrompt()
                    .replace("{ai_name}", aiName)
                    .replace("{player_context}", playerContext)
                    .replace("{history}", historySummary)
                    .replace("{inventory}", inventory)
                    .replace("{effects}", effects)
                    .replace("{world_resources}", worldResources)
                    .replace("{message}", message);
        }
    }

    private void executeAdminRequest(Player player, String prompt, String originalMessage) {
        String name = player.getName();
        Global.executor.submit(() -> {
            try {
                api.chatCompletionAsync(prompt, "deepseek-reasoner", 2000).thenAccept(resp -> {
                    String finalResp;
                    if (resp == null || resp.trim().isEmpty()) {
                        finalResp = "/say " + aiName + " 返回为空";
                    } else if (!validateCommand(resp)) {
                        finalResp = "/say " + aiName + " 生成的命令无效";
                    } else {
                        finalResp = resp;
                    }
                    String finalResp1 = finalResp;
                    Bukkit.getScheduler().runTask(config.getPlugin(), () -> {
                        chatHistory.addMessage(name, "管理员命令: " + finalResp1);
                        Bukkit.broadcastMessage("§c[AI管理员] " + name + ": " + originalMessage);
                        executeCommands(finalResp1, name, player);
                    });
                }).exceptionally(throwable -> {
                    String fallbackResp = "/say " + aiName + " 请求失败";
                    Bukkit.getScheduler().runTask(config.getPlugin(), () -> {
                        chatHistory.addMessage(name, "管理员命令: " + fallbackResp);
                        Bukkit.broadcastMessage("§c[AI管理员] " + name + ": " + originalMessage);
                        executeCommands(fallbackResp, name, player);
                    });
                    return null;
                });
            } catch (Exception e) {
                String fallbackResp = "/say " + aiName + " 处理失败";
                Bukkit.getScheduler().runTask(config.getPlugin(), () -> {
                    chatHistory.addMessage(name, "管理员命令: " + fallbackResp);
                    Bukkit.broadcastMessage("§c[AI管理员] " + name + ": " + originalMessage);
                    executeCommands(fallbackResp, name, player);
                });
            }
        });
    }

    private boolean validateCommand(String command) {
        if (command == null || !command.startsWith("/")) return false;
        String[] lines = command.split("\n");
        for (String line : lines) {
            if (!line.startsWith("/")) continue;
            String cmdName = line.split(" ")[0].substring(1);
            if (cmdName.equalsIgnoreCase("stop") || cmdName.equalsIgnoreCase("reload") || cmdName.equalsIgnoreCase("op")) {
                return false;
            }
            if (!COMMAND_WHITELIST.isEmpty() && !COMMAND_WHITELIST.contains(cmdName)) {
                return false;
            }
        }
        return true;
    }

    private void executeChatRequest(Player player, String prompt, String originalMessage, boolean isPublic, Consumer<String> callback) {
        String name = player.getName();
        Global.executor.submit(() -> {
            try {
                api.chatCompletionAsync(prompt, getPlayerModel(name), 2000).thenAccept(resp -> {
                    String finalResp = (resp == null || resp.trim().isEmpty()) ? "§b" + aiName + ": 我不太明白你在说什么，能再解释一下吗？" : resp;
                    Bukkit.getScheduler().runTask(config.getPlugin(), () -> {
                        chatHistory.addMessage(name, aiName + ": " + finalResp);
                        if (isPublic) {
                            Bukkit.broadcastMessage("§b[AI公开] " + name + ": " + originalMessage);
                            player.sendMessage("§b" + finalResp);
                        } else {
                            player.sendMessage("§b[AI私聊] 你: " + originalMessage);
                            player.sendMessage("§b" + finalResp);
                        }
                        if (callback != null) callback.accept(finalResp);
                        chatConfig.incrementMessageCount(name);
                    });
                }).exceptionally(throwable -> {
                    Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage("§cAI 请求失败，请稍后再试！"));
                    return null;
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage("§cAI 处理失败，请联系管理员！"));
            }
        });
    }

    private void executeCommands(String response, String playerName, Player player) {
        String[] commands = response.split("\n");
        for (int i = 0; i < commands.length; i++) {
            String command = commands[i].trim();
            if (!command.startsWith("/")) continue;
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.substring(1));
                        Bukkit.broadcastMessage("§c[AI执行] " + playerName + ": " + command);
                    } catch (Exception e) {
                        Bukkit.broadcastMessage("§c[" + aiName + "错误] 命令执行失败: " + e.getMessage());
                    }
                }
            }.runTaskLater(config.getPlugin(), i * 10L);
        }
    }

    private void startMessageReset() {
        long resetInterval = config.getConfig().getLong("message_reset_interval", 72000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                chatConfig.resetMessageCount();
                playerModels.keySet().removeIf(name -> Bukkit.getPlayer(name) == null);
                lastRequestTime.keySet().removeIf(name -> Bukkit.getPlayer(name) == null);
            }
        }.runTaskTimerAsynchronously(config.getPlugin(), 0L, resetInterval);
    }
}