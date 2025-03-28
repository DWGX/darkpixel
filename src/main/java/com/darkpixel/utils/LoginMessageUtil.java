package com.darkpixel.utils;

import com.darkpixel.Global;
import com.darkpixel.ai.AiChatHandler;
import com.darkpixel.manager.ConfigManager;
import com.darkpixel.rank.RankData;
import com.darkpixel.rank.RankManager;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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
        // 注册事件监听器
        context.getPlugin().getServer().getPluginManager().registerEvents(this, context.getPlugin());
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        UUID uuid = player.getUniqueId();

        // 检查 Bukkit BanList
        BanEntry banEntry = Bukkit.getBanList(BanList.Type.NAME).getBanEntry(playerName);
        if (banEntry != null) {
            long banUntil = banEntry.getExpiration() == null ? -1 : banEntry.getExpiration().getTime();
            if (banUntil == -1 || banUntil > System.currentTimeMillis()) {
                String reason = banEntry.getReason() != null ? banEntry.getReason() : "未指定原因";
                String kickMessage = "你已被封禁" + (banUntil == -1 ? "永久" : "，剩余 " + ((banUntil - System.currentTimeMillis()) / 60000) + " 分钟") + "\n原因：" + reason;
                event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
                context.getPlugin().getLogger().info(playerName + " 尝试登录但被封禁，原因：" + reason);
                return;
            }
        }

        // 检查 RankData（备用检查，确保与数据库一致）
        RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
        long now = System.currentTimeMillis();
        if (data.getBanUntil() > now || data.getBanUntil() == -1) {
            String reason = data.getBanReason() != null ? data.getBanReason() : "未指定原因";
            String kickMessage = "你已被封禁" + (data.getBanUntil() == -1 ? "永久" : "，剩余 " + ((data.getBanUntil() - now) / 60000) + " 分钟") + "\n原因：" + reason;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
            context.getPlugin().getLogger().info(playerName + " 尝试登录但被封禁，原因：" + reason);
            return;
        }

        // 允许登录
        event.allow();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String displayName = player.getDisplayName() != null ? player.getDisplayName() : playerName;
        String rank = rankManager.getRank(player);
        int score = rankManager.getScore(player);
        Particle particle = rankManager.getJoinParticle(player);
        String joinMessage = rankManager.getJoinMessage(player).replace("{player}", displayName);
        Global.executor.submit(() -> playerData.updatePlayer(player));
        int loginCount = playerData.getPlayerInfo(playerName).loginCount;

        // 设置默认加入消息
        event.setJoinMessage("§7Lobby §8| §a" + playerName + " (" + rank + ") 欢迎第 " + loginCount + " 次加入黑像素服务器");

        // 使用 tellraw 发送酷炫欢迎消息
        String tellrawCommand = "tellraw @a [{\"text\":\"§l§6欢迎 \",\"bold\":true},{\"text\":\"" + displayName + "\",\"color\":\"aqua\",\"bold\":true},{\"text\":\" §e加入服务器！\",\"bold\":true},{\"text\":\" (§b第 " + loginCount + " 次§e)\",\"color\":\"yellow\"}]";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), tellrawCommand);

        player.getWorld().spawnParticle(particle, player.getLocation(), 100);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // 处理 OP 和 OldCombatMechanics 指令
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                List<String> groups = rankManager.getPlayerGroups(player);
                boolean shouldBeOp = groups.contains("op") || Bukkit.getOperators().stream().anyMatch(op -> op.getUniqueId().equals(player.getUniqueId()));

                // 如果玩家不应该是 OP，临时赋予 OP 并执行指令
                if (!shouldBeOp && !player.isOp()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "op " + playerName);
                    player.performCommand("oldcombatmechanics mode old");
                    // 立即移除 OP 权限
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (player.isOnline() && player.isOp()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "deop " + playerName);
                            }
                        }
                    }.runTaskLater(context.getPlugin(), 5L); // 缩短延迟到 5 tick
                } else if (shouldBeOp && !player.isOp()) {
                    // 如果玩家应该是 OP，永久赋予
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "op " + playerName);
                    player.sendMessage("§a已授予你 OP 权限！");
                    player.performCommand("oldcombatmechanics mode old");
                } else if (player.isOp()) {
                    // 如果玩家已经是 OP，直接执行指令
                    player.performCommand("oldcombatmechanics mode old");
                }
            }
        }.runTaskLater(context.getPlugin(), 20L);

        // AI 欢迎消息
        boolean aiWelcomeEnabled = configManager.getConfig().getBoolean("ai_welcome_enabled", true);
        if (aiWelcomeEnabled && (System.currentTimeMillis() - lastAiWelcome.getOrDefault(player.getUniqueId(), 0L)) >= configManager.getAiWelcomeInterval()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Global.executor.submit(() -> aiChat.sendMessage(player, "玩家 " + playerName + "（Rank: " + rank + "，分数: " + score + "）第 " + loginCount + " 次加入服务器，坐标: " +
                            player.getLocation().getBlockX() + "," + player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ() + "，请用中文生成一个自然、友好的欢迎消息", false, response ->
                            player.sendMessage("§b" + response)));
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
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
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