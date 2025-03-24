package com.darkpixel.utils;

import com.darkpixel.Global;
import com.darkpixel.ai.AiChatHandler;
import com.darkpixel.manager.ConfigManager;
import com.darkpixel.rank.ChatListener;
import com.darkpixel.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LoginMessageUtil implements Listener {
    private final PlayerData playerData;
    private final AiChatHandler aiChat;
    private final RankManager rankManager;
    private final Map<UUID, Long> lastAiWelcome = new HashMap<>();
    private final ConfigManager configManager;
    private final Global context;

    public LoginMessageUtil(Global context) {
        this.context = context;
        this.playerData = context.getPlayerData();
        this.aiChat = context.getAiChat();
        this.rankManager = context.getRankManager();
        this.configManager = context.getConfigManager();
        context.getPlugin().getCommand("toggleaiwelcome").setExecutor(new ToggleAiWelcomeCommand(this.context));
        context.getPlugin().getServer().getPluginManager().registerEvents(new ChatListener(playerData, rankManager), context.getPlugin());
        context.getPlugin().getServer().getPluginManager().registerEvents(new com.darkpixel.utils.effects.PlayerJoinEffects(playerData), context.getPlugin());
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        event.setResult(PlayerLoginEvent.Result.ALLOWED);
        event.setKickMessage("");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String rank = rankManager.getRank(player);
        int score = rankManager.getScore(player);
        Global.executor.submit(() -> playerData.updatePlayer(player));
        int loginCount = playerData.getPlayerInfo(playerName).loginCount;
        event.setJoinMessage("§7Lobby §8| §a" + playerName + " (" + rank + ") 欢迎第 " + loginCount + " 次加入黑像素服务器");
        context.getPlugin().getServer().broadcastMessage("§6✨§c尊贵的" + rank + " §e" + playerName + "§c 降临服务器！§6✨ §b(｡>∀<｡) 大佬驾到，全体起立！");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                // 检查 OP 身份组并赋予权限
                List<String> groups = rankManager.getPlayerGroups(player);
                boolean shouldBeOp = groups.contains("op") || Bukkit.getOperators().stream().anyMatch(op -> op.getUniqueId().equals(player.getUniqueId()));
                if (shouldBeOp && !player.isOp()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "op " + playerName);
                    player.sendMessage("§a已授予你 OP 权限！");
                }

                // 设置 OldCombatMechanics 模式（避免冲突）
                boolean wasOp = player.isOp();
                if (!wasOp) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "op " + playerName);
                player.performCommand("oldcombatmechanics mode old");
                if (!shouldBeOp) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (player.isOnline() && player.isOp()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "deop " + playerName);
                            }
                        }
                    }.runTaskLater(configManager.getPlugin(), 10L);
                }
            }
        }.runTaskLater(configManager.getPlugin(), 20L);

        boolean aiWelcomeEnabled = configManager.getConfig().getBoolean("ai_welcome_enabled", true);
        if (aiWelcomeEnabled && (System.currentTimeMillis() - lastAiWelcome.getOrDefault(player.getUniqueId(), 0L)) >= configManager.getAiWelcomeInterval()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Global.executor.submit(() -> aiChat.sendMessage(player, "玩家 " + playerName + "（Rank: " + rank + "，分数: " + score + "）第 " + loginCount + " 次加入服务器，坐标: " +
                            player.getLocation().getBlockX() + "," + player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ() + "，请用中文生成一个自然、友好的欢迎消息", false, response ->
                            player.sendMessage("§b欢迎回来，" + playerName + "（" + rank + "）！第" + loginCount + "次光临，分数 " + score + "，快去冒险吧~^^")));
                }
            }.runTaskLater(configManager.getPlugin(), 20L);
            lastAiWelcome.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    public static class ToggleAiWelcomeCommand implements CommandExecutor {
        private final Global context;

        public ToggleAiWelcomeCommand(Global context) {
            this.context = context;
        }

        @Override
        public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
            if (!sender.hasPermission("darkpixel.admin")) {
                sender.sendMessage("§c需要管理员权限！");
                return true;
            }
            boolean enabled = !context.getConfigManager().getConfig().getBoolean("ai_welcome_enabled", true);
            context.getConfigManager().getConfig().set("ai_welcome_enabled", enabled);
            Global.executor.submit(() -> context.getConfigManager().saveConfig("config.yml"));
            sender.sendMessage("§aAI欢迎消息已" + (enabled ? "开启" : "关闭"));
            return true;
        }
    }
}