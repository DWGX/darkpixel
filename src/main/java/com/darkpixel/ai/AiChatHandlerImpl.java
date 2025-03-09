package com.darkpixel.ai;

import com.darkpixel.Global;
import com.darkpixel.anticheat.AntiCheatHandler.PlayerCheatData; // 正确导入
import com.darkpixel.manager.ConfigManager;
import com.darkpixel.utils.LogUtil;
import com.darkpixel.utils.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    public AiChatHandlerImpl(ConfigManager config, Global context) {
        this.config = config;
        this.context = context;
        this.aiName = config.getAiName();
        this.api = new ApiClient(config, context);
        this.chatConfig = new AiChatConfig(config);
        this.chatHistory = new AiChatHistory(config);
        startMessageReset();
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
    public CompletableFuture<String> sendMessage(Player player, String message, boolean isPublic) {
        CompletableFuture<String> future = new CompletableFuture<>();
        String name = player.getName();
        long now = System.currentTimeMillis();

        String limitMsg = checkSendLimits(name, player.isOp(), now);
        if (limitMsg != null) {
            Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage(limitMsg));
            future.complete(limitMsg);
            return future;
        }

        lastRequestTime.put(name, now);
        PlayerData.PlayerInfo playerInfo = context.getPlayerData().getPlayerInfo(name);
        String prompt = buildPromptForPlayer(player, message, playerInfo);

        api.chatCompletionAsync(prompt, getPlayerModel(name), 2000).thenAccept(resp -> {
            String finalResp = resp != null && !resp.trim().isEmpty() ? resp : generateFallbackResponse(name);
            Bukkit.getScheduler().runTask(config.getPlugin(), () -> {
                chatHistory.addMessage(name, aiName + ": " + finalResp);
                chatHistory.saveChatHistoryAsync();
                broadcastResponse(player, message, finalResp, isPublic);
                chatConfig.incrementMessageCount(name);
                future.complete(finalResp);
            });
        }).exceptionally(throwable -> {
            String errorMsg = "§cAI 请求失败: " + throwable.getMessage();
            Bukkit.getScheduler().runTask(config.getPlugin(), () -> {
                player.sendMessage(errorMsg);
                LogUtil.severe("AI 请求异常: " + throwable.getMessage());
                future.complete(errorMsg);
            });
            return null;
        });

        return future;
    }

    @Override
    public void sendMessage(Player player, String message, boolean isPublic, Consumer<String> callback) {
        sendMessage(player, message, isPublic).thenAccept(callback);
    }

    @Override
    public void sendAdminBroadcast(Player player, String message) {
        String name = player.getName();
        if (!player.hasPermission("darkpixel.admin")) {
            Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage("§c只有管理员可以用这个命令！"));
            return;
        }
        lock.lock();
        try {
            chatHistory.addMessage(name, "管理员: " + message);
            PlayerData.PlayerInfo playerInfo = context.getPlayerData().getPlayerInfo(name);
            String playerContext = buildPlayerContext(player, playerInfo);
            String prompt = "你是一个Minecraft服务器AI助手，名字叫“" + aiName + "”。根据管理员输入，生成命令（以 / 开头，多行用 \\n 分隔，最后附 'AI: <简短回复>'）。\n" +
                    "玩家状态: " + playerContext + "\n" +
                    "输入: " + message;
            api.chatCompletionAsync(prompt, "deepseek-reasoner", 2000).thenAccept(resp -> {
                String finalResp = resp != null && resp.startsWith("/") ? resp : "/say " + aiName + " 听不懂管理员说啥 AI: 试试再说清楚点吧";
                Bukkit.getScheduler().runTask(config.getPlugin(), () -> {
                    chatHistory.addMessage(name, aiName + ": " + finalResp);
                    Bukkit.broadcastMessage("§c[AI管理员广播] " + name + ": " + message);
                    executeCommands(finalResp, name);
                });
            });
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
        if (!player.hasPermission("darkpixel.admin")) {
            Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage("§c你没权限！"));
            return;
        }
        String[] args = message.split(" ");
        String name = player.getName();
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
        }
    }
    @Override
    public void shutdown() {
        chatHistory.shutdown();
    }

    private String buildPromptForPlayer(Player player, String message, PlayerData.PlayerInfo playerInfo) {
        String name = player.getName();
        String playerContext = buildPlayerContext(player, playerInfo);
        String inventory = playerInfo.getInventoryDescription();
        String effects = playerInfo.getEffectsDescription();
        String worldResources = context.getWorldData().analyzeWorld(player.getWorld());
        String historySummary = chatHistory.getHistorySummary(name);
        String recentActions = getRecentActions(player);
        String nearbyEntities = getNearbyEntities(player);

        chatHistory.addMessage(name, "用户: " + message);

        return "你是一个Minecraft 1.21.4服务器AI助手，名字叫“" + aiName + "”。根据玩家输入和上下文，生成有针对性的回复或命令（若适用，以 / 开头，多行以 \\n 分隔，最后附 'AI: <简短回复>'）。\n" +
                "玩家状态: " + playerContext + "\n" +
                "最近行为: " + recentActions + "\n" +
                "附近实体: " + nearbyEntities + "\n" +
                "历史摘要: " + (historySummary.isEmpty() ? "无记录" : historySummary) + "\n" +
                "背包: " + inventory + "\n" +
                "效果: " + effects + "\n" +
                "世界资源: " + worldResources + "\n" +
                "当前输入: " + (message.isEmpty() ? "玩家没说啥，随便给点建议吧" : message);
    }

    private String buildPlayerContext(Player player, PlayerData.PlayerInfo info) {
        return "玩家名: " + player.getName() + ", 位置: " + player.getLocation().getBlockX() + "," +
                player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ() + ", 世界: " +
                player.getWorld().getName() + ", 生命值: " + player.getHealth() + ", 饥饿值: " + player.getFoodLevel();
    }

    private String getRecentActions(Player player) {
        PlayerCheatData data = context.getAntiCheatHandler().cheatData.get(player.getUniqueId());
        if (data == null) return "无最近行为";
        long now = System.currentTimeMillis();
        List<String> actions = new ArrayList<>();
        if (now - data.lastHitTime < 5000) actions.add("最近攻击");
        if (now - data.lastMoveTime < 5000) actions.add("最近移动");
        return actions.isEmpty() ? "无最近行为" : String.join(", ", actions);
    }

    private String getNearbyEntities(Player player) {
        return player.getNearbyEntities(10, 10, 10).stream()
                .map(e -> e.getType().name())
                .collect(Collectors.joining(", "));
    }

    private String checkSendLimits(String name, boolean isOp, long now) {
        if (!canSendMessage(name, isOp)) return "§c每小时只能发 " + getPlayerMessageLimit(name) + " 条消息，等等吧";
        Long lastTime = lastRequestTime.get(name);
        if (lastTime != null && (now - lastTime) < config.getConfig().getLong("ai_chat.cooldown_ms", 1000L)) return "§c慢点，" + aiName + " 还在想呢";
        return null;
    }

    private String generateFallbackResponse(String name) {
        return "/give " + name + " minecraft:cookie 2\nAI: 没听懂，给你两块饼干吧";
    }

    private void broadcastResponse(Player player, String message, String response, boolean isPublic) {
        String name = player.getName();
        if (isPublic) {
            Bukkit.broadcastMessage("§b[AI公开] " + name + ": " + message);
            player.sendMessage("§b" + response);
        } else {
            player.sendMessage("§b[AI私聊] 你: " + message);
            player.sendMessage("§b" + response);
        }
    }

    private void executeCommands(String response, String playerName) {
        String[] commands = response.split("\n");
        Player player = Bukkit.getPlayer(playerName);
        for (int i = 0; i < commands.length; i++) {
            String command = commands[i].trim();
            if (!command.startsWith("/")) command = "/say " + command;
            String[] parts = command.split(" AI: ");
            String cmd = parts[0];
            String reply = parts.length > 1 ? parts[1] : "已执行";
            if (!player.hasPermission("darkpixel.ai.execute")) {
                player.sendMessage("§c你没有权限执行此命令");
                continue;
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring(1));
                    Bukkit.broadcastMessage("§c[AI执行] " + playerName + ": " + cmd + " AI: " + reply);
                }
            }.runTaskLater(config.getPlugin(), i * 5L);
        }
    }

    private void startMessageReset() {
        new BukkitRunnable() {
            @Override
            public void run() {
                chatConfig.resetMessageCount();
            }
        }.runTaskTimer(config.getPlugin(), 0L, 72000L);
    }
}