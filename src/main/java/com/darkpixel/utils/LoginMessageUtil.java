package com.darkpixel.utils;

import com.darkpixel.Global;
import com.darkpixel.ai.AiChatHandler;
import com.darkpixel.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginMessageUtil implements Listener {
    private final PlayerData playerData;
    private final AiChatHandler aiChat;
    private final Map<UUID, Long> lastAiWelcome = new HashMap<>();
    private final ConfigManager configManager;
    private final Global context;

    public LoginMessageUtil(Global context) {
        this.context = context;
        this.playerData = context.getPlayerData();
        this.aiChat = context.getAiChat();
        this.configManager = context.getConfigManager();
        registerCommands(context.getPlugin());
    }

    private void registerCommands(JavaPlugin plugin) {
        plugin.getCommand("toggleaiwelcome").setExecutor(new ToggleAiWelcomeCommand(this.context));
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
        playerData.updatePlayer(player);
        int loginCount = playerData.getPlayerInfo(playerName).loginCount;
        event.setJoinMessage("§7Lobby §8| §a" + playerName + " 欢迎第 " + loginCount + " 次加入黑像素服务器");

        // 处理带有 "op" tag 或权限的玩家
        boolean isTaggedOp = player.getScoreboardTags().contains("op") || player.hasPermission("darkpixel.op");
        if (isTaggedOp) {
            String opWelcomeMessage = "§6✨§c尊贵的OP §e" + playerName + "§c 降临服务器！§6✨ §b(｡>∀<｡) 大佬驾到，全体起立！";
            context.getPlugin().getServer().broadcastMessage(opWelcomeMessage);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;
                    // 确保 OP 状态
                    if (!player.isOp()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "op " + playerName);
                    }
                    // 执行指令，但不再自动移除 OP
                    player.performCommand("oldcombatmechanics mode old");
                }
            }.runTaskLater(configManager.getPlugin(), 20L);
        } else {
            // 非 OP 玩家的临时 OP 处理逻辑
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || player.isOp()) return;

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "op " + playerName);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (player.isOnline() && player.isOp()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "deop " + playerName);
                            }
                        }
                    }.runTaskLater(configManager.getPlugin(), 10L);
                }
            }.runTaskLater(configManager.getPlugin(), 10L);
        }

        boolean aiWelcomeEnabled = configManager.getConfig("config.yml").getBoolean("ai_welcome_enabled", true);
        if (aiWelcomeEnabled && shouldSendAiWelcome(player)) {
            sendAiWelcome(player, loginCount);
            lastAiWelcome.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    private boolean shouldSendAiWelcome(Player player) {
        long lastTime = lastAiWelcome.getOrDefault(player.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        long interval = configManager.getAiWelcomeInterval();
        return (currentTime - lastTime) >= interval;
    }

    private void sendAiWelcome(Player player, int loginCount) {
        String prompt = "玩家 " + player.getName() + " 第 " + loginCount + " 次加入服务器，坐标: " +
                player.getLocation().getBlockX() + "," + player.getLocation().getBlockY() + "," +
                player.getLocation().getBlockZ() + "，请用中文生成一个自然、友好的欢迎消息";
        new BukkitRunnable() {
            @Override
            public void run() {
                aiChat.sendMessage(player, prompt, false, response -> {
                    if (response.contains("§c")) {
                        player.sendMessage("§b欢迎回来，" + player.getName() + "！第" + loginCount + "次光临，坐标已就位，快去冒险吧~^^");
                    }
                });
            }
        }.runTaskLater(configManager.getPlugin(), 20L);
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
            boolean enabled = !context.getConfigManager().getConfig("config.yml").getBoolean("ai_welcome_enabled", true);
            context.getConfigManager().getConfig("config.yml").set("ai_welcome_enabled", enabled);
            context.getConfigManager().saveConfig("config.yml");
            sender.sendMessage("§aAI欢迎消息已" + (enabled ? "开启" : "关闭"));
            return true;
        }
    }
}