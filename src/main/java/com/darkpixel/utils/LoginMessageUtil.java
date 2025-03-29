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
    private final ConfigManager configManager;
    private final Global context;
    private final MessageHandler messageHandler;
    private final Map<UUID, Long> lastAiWelcome = new HashMap<>();
    private final Map<UUID, Boolean> hasSetCombatMode = new HashMap<>();

    public LoginMessageUtil(Global context) {
        this.context = context;
        this.playerData = context.getPlayerData();
        this.aiChat = context.getAiChat();
        this.rankManager = context.getRankManager();
        this.configManager = context.getConfigManager();
        this.messageHandler = new MessageHandler(context);
        context.getPlugin().getCommand("toggleaiwelcome").setExecutor(new ToggleAiWelcomeCommand(context));
        context.getPlugin().getServer().getPluginManager().registerEvents(this, context.getPlugin());
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        UUID uuid = player.getUniqueId();

        BanEntry banEntry = Bukkit.getBanList(BanList.Type.NAME).getBanEntry(playerName);
        if (banEntry != null) {
            long banUntil = banEntry.getExpiration() == null ? -1 : banEntry.getExpiration().getTime();
            if (banUntil == -1 || banUntil > System.currentTimeMillis()) {
                String reason = banEntry.getReason() != null ? banEntry.getReason() : "未指定原因";
                String kickMessage = "你已被封禁" + (banUntil == -1 ? "永久" : "，剩余 " + ((banUntil - System.currentTimeMillis()) / 60000) + " 分钟") + "\n原因：" + reason;
                event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
                return;
            }
        }

        RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
        long now = System.currentTimeMillis();
        if (data.getBanUntil() > now || data.getBanUntil() == -1) {
            String reason = data.getBanReason() != null ? data.getBanReason() : "未指定原因";
            String kickMessage = "你已被封禁" + (data.getBanUntil() == -1 ? "永久" : "，剩余 " + ((data.getBanUntil() - now) / 60000) + " 分钟") + "\n原因：" + reason;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
            return;
        }

        event.allow();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        String rank = rankManager.getRank(player);
        int score = rankManager.getScore(player);
        Particle particle = rankManager.getJoinParticle(player);

        // 添加调试日志
        context.getPlugin().getLogger().info("PlayerJoinEvent triggered for " + playerName + " at " + System.currentTimeMillis());

        playerData.updatePlayer(player);
        int loginCount = playerData.getPlayerInfo(playerName).login_count;

        event.setJoinMessage(null);
        messageHandler.sendJoinMessage(player, rank, loginCount);

        player.getWorld().spawnParticle(particle, player.getLocation(), 100);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                List<String> groups = rankManager.getPlayerGroups(player);
                boolean shouldBeOp = groups.contains("op") || Bukkit.getOperators().stream().anyMatch(op -> op.getUniqueId().equals(player.getUniqueId()));

                if (shouldBeOp && !player.isOp()) {
                    player.setOp(true);
                }

                if (!hasSetCombatMode.getOrDefault(uuid, false)) {
                    player.performCommand("oldcombatmechanics mode old");
                    hasSetCombatMode.put(uuid, true);
                }
            }
        }.runTaskLater(context.getPlugin(), 20L);

        if (configManager.getConfig().getBoolean("ai_welcome_enabled", true) &&
                (System.currentTimeMillis() - lastAiWelcome.getOrDefault(uuid, 0L)) >= configManager.getAiWelcomeInterval()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Global.executor.submit(() -> messageHandler.sendAiWelcomeMessage(player, rank, score, loginCount));
                }
            }.runTaskLater(configManager.getPlugin(), 20L);
            lastAiWelcome.put(uuid, System.currentTimeMillis());
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