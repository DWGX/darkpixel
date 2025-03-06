package com.darkpixel.ai;

import com.darkpixel.Global;
import com.darkpixel.manager.ConfigManager;
import com.darkpixel.utils.LogUtil;
import com.darkpixel.utils.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;

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
        LogUtil.info("AI Chat 配置已重新加载");
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
            LogUtil.info("玩家 " + player + " 的AI模型切换为: " + model);
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
        chatHistory.addMessage(player.getName(), "用户: " + message);
        return buildPrompt(player.getName(), message, playerContext, inventory, effects, worldResources, false);
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
        LogUtil.info("设置玩家 " + player + " 的消息上限为: " + limit);
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

    private void executeAdminRequest(Player player, String prompt, String originalMessage) {
        String name = player.getName();
        Global.executor.submit(() -> {
            try {
                api.chatCompletionAsync(prompt, "deepseek-reasoner", 2000).thenAccept(resp -> {
                    String finalResp = resp != null && !resp.trim().isEmpty() && resp.startsWith("/") ? resp : generateCreativeResponse(player, originalMessage);
                    Bukkit.getScheduler().runTask(config.getPlugin(), () -> {
                        chatHistory.addMessage(name, aiName + ": " + finalResp);
                        Bukkit.broadcastMessage("§c[AI管理员广播] " + name + ": " + originalMessage);
                        executeCommands(finalResp, name, player);
                    });
                }).exceptionally(throwable -> {
                    String fallbackResp = "/say " + aiName + " 请求失败 AI: 出错了，稍后再试吧！";
                    Bukkit.getScheduler().runTask(config.getPlugin(), () -> {
                        chatHistory.addMessage(name, aiName + ": " + fallbackResp);
                        Bukkit.broadcastMessage("§c[AI管理员广播] " + name + ": " + originalMessage);
                        executeCommands(fallbackResp, name, player);
                    });
                    LogUtil.severe("AI 管理员请求异常: " + throwable.getMessage());
                    return null;
                });
            } catch (Exception e) {
                String fallbackResp = "/say " + aiName + " 处理失败 AI: 请联系管理员！";
                Bukkit.getScheduler().runTask(config.getPlugin(), () -> {
                    chatHistory.addMessage(name, aiName + ": " + fallbackResp);
                    Bukkit.broadcastMessage("§c[AI管理员广播] " + name + ": " + originalMessage);
                    executeCommands(fallbackResp, name, player);
                });
                LogUtil.severe("AI 管理员请求处理失败: " + e.getMessage());
            }
        });
    }

    private String generateCreativeResponse(Player player, String originalMessage) {
        String name = player.getName();
        PlayerData.PlayerInfo playerInfo = context.getPlayerData().getPlayerInfo(name);
        String playerContext = buildPlayerContext(player, playerInfo);
        String inventory = playerInfo.getInventoryDescription();
        String effects = playerInfo.getEffectsDescription();
        String worldResources = context.getWorldData().analyzeWorld(player.getWorld());
        String historySummary = getHistorySummary(name);
        String enhancedPrompt = "你是一个Minecraft 1.21.4服务器AI助手，名字叫“" + aiName + "”。根据玩家输入和上下文，生成任意Minecraft命令（每行以 / 开头，多行以 \\n 分隔，最后附 'AI: <简短回复>'）。你的目标是自由发挥创意，生成简单或高级的内容，例如：简单命令（如 /give 物品），或高级逻辑（如生成建筑、红石装置、随机事件）。必须始终返回命令形式，不允许返回纯文本或‘无法理解’。根据玩家状态、背包、世界资源和历史记录自主决定内容。\n" +
                "玩家状态: " + (playerContext != null ? playerContext : "未知玩家状态") + "\n" +
                "历史摘要: " + historySummary + "\n" +
                "背包: " + (inventory != null ? inventory : "背包未知") + "\n" +
                "效果: " + (effects != null ? effects : "无效果") + "\n" +
                "世界资源: " + (worldResources != null ? worldResources : "世界资源未知") + "\n" +
                "当前输入: " + (originalMessage.isEmpty() ? "玩家未提供具体要求，请自由发挥" : originalMessage);
        try {
            String result = api.chatCompletionAsync(enhancedPrompt, "deepseek-reasoner", 2000).join();
            if (result == null || result.trim().isEmpty() || !result.startsWith("/")) {
                Random rand = new Random();
                if (originalMessage.isEmpty() || originalMessage.length() < 5) {
                    return "/give " + name + " minecraft:golden_apple 3\nAI: 给你点金苹果，尝尝吧！";
                } else {
                    int x = player.getLocation().getBlockX();
                    int y = player.getLocation().getBlockY();
                    int z = player.getLocation().getBlockZ();
                    return "/fill " + x + " " + y + " " + z + " " + (x + 2) + " " + (y + 2) + " " + (z + 2) + " iron_block\n" +
                            "/setblock " + (x + 1) + " " + (y + 3) + " " + (z + 1) + " beacon\n" +
                            "/effect give " + name + " regeneration 60 1\nAI: 为你建了个小灯塔，享受加成吧！";
                }
            }
            return result;
        } catch (Exception e) {
            LogUtil.severe("创意生成失败: " + e.getMessage());
            return "/give " + name + " minecraft:apple 1\nAI: 出错了，给你个苹果安慰一下！";
        }
    }

    private String buildPrompt(String name, String message, String playerContext, String inventory, String effects, String worldResources, boolean isAdmin) {
        String rawPrompt = config.getConfig().getString(isAdmin ? "ai_admin_prompt" : "ai_public_prompt", "默认提示未定义");
        String historySummary = getHistorySummary(name);
        if (isAdmin) {
            rawPrompt = "你是一个Minecraft 1.21.4服务器AI助手，名字叫“" + aiName + "”。根据玩家输入和上下文，生成Minecraft命令（每行以 / 开头，多行以 \\n 分隔，最后附 'AI: <简短回复>'）。可以自由发挥，生成实用、有趣或高级的内容，如建筑、红石装置或随机事件。参考相关命令但不拘泥于模板。\n" +
                    "玩家状态: " + (playerContext != null ? playerContext : "未知玩家状态") + "\n" +
                    "历史摘要: " + historySummary + "\n" +
                    "背包: " + (inventory != null ? inventory : "背包未知") + "\n" +
                    "效果: " + (effects != null ? effects : "无效果") + "\n" +
                    "世界资源: " + (worldResources != null ? worldResources : "世界资源未知") + "\n" +
                    "当前输入: " + (message.isEmpty() ? "玩家未提供具体要求，请自由发挥生成命令" : message);
        } else {
            rawPrompt = rawPrompt
                    .replace("{ai_name}", aiName)
                    .replace("{player_context}", playerContext != null ? playerContext : "未知玩家状态")
                    .replace("{history}", historySummary)
                    .replace("{inventory}", inventory != null ? inventory : "背包未知")
                    .replace("{effects}", effects != null ? effects : "无效果")
                    .replace("{world_resources}", worldResources != null ? worldResources : "世界资源未知")
                    .replace("{message}", message.isEmpty() ? "玩家未提供具体要求，请提供有趣建议" : message);
        }
        return rawPrompt;
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
                    LogUtil.severe("AI 请求异常: " + throwable.getMessage());
                    return null;
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(config.getPlugin(), () -> player.sendMessage("§cAI 处理失败，请联系管理员！"));
                LogUtil.severe("AI 请求处理失败: " + e.getMessage());
            }
        });
    }

    private void executeCommands(String response, String playerName, Player player) {
        String[] commands = response.split("\n");
        for (int i = 0; i < commands.length; i++) {
            String command = commands[i].trim();
            if (!command.startsWith("/")) command = "/say " + command;
            String[] parts = command.split(" AI: ");
            String cmd = parts[0];
            String reply = parts.length > 1 ? parts[1] : "操作完成";
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring(1));
                        Bukkit.broadcastMessage("§c[AI执行] " + playerName + ": " + cmd + " AI: " + reply);
                    } catch (Exception e) {
                        Bukkit.broadcastMessage("§c[" + aiName + "错误] 命令执行失败: " + e.getMessage());
                        LogUtil.severe("命令执行失败: " + cmd + " - " + e.getMessage());
                    }
                }
            }.runTaskLater(config.getPlugin(), i * 5L);
        }
    }

    private void startMessageReset() {
        long resetInterval = config.getConfig().getLong("message_reset_interval", 72000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                chatConfig.resetMessageCount();
                LogUtil.info("玩家消息计数已重置~");
            }
        }.runTaskTimer(config.getPlugin(), 0L, resetInterval);
    }
}