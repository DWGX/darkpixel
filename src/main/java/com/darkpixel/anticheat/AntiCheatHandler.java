package com.darkpixel.anticheat;

import com.darkpixel.Global;
import com.darkpixel.ai.AiChatHandler;
import com.darkpixel.anticheat.detectors.*;
import com.darkpixel.utils.FileUtil;
import com.darkpixel.utils.LogUtil;
import com.darkpixel.utils.PlayerData;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AntiCheatHandler implements Listener, CommandExecutor, TabCompleter {
    private final Global context;
    private final AiChatHandler aiChat;
    private final PlayerData playerData;
    private final File configFile;
    private YamlConfiguration config;
    private boolean enabled;
    private boolean alertsEnabled = true;
    private final Map<UUID, PlayerCheatData> cheatData = new ConcurrentHashMap<>();
    private final Map<UUID, List<Report>> reports = new ConcurrentHashMap<>();
    private final Map<UUID, Map<CheatType, Integer>> triggerCounts = new ConcurrentHashMap<>();
    private final Map<CheatType, Detector> detectors = new EnumMap<>(CheatType.class);
    private final Map<String, ReportTicket> reportTickets = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> underAIReview = new ConcurrentHashMap<>();
    private final Map<UUID, List<CheatHistoryEntry>> cheatHistory = new ConcurrentHashMap<>();
    private int ticketCounter = 0;

    public AntiCheatHandler(Global context) {
        this.context = context;
        this.aiChat = context.getAiChat();
        this.playerData = context.getPlayerData();
        this.configFile = new File(context.getPlugin().getDataFolder(), "darkac.yml");
        loadConfig();
        initDetectors();
        context.getPlugin().getServer().getPluginManager().registerEvents(this, context.getPlugin());
        context.getPlugin().getCommand("darkac").setExecutor(this);
        context.getPlugin().getCommand("darkac").setTabCompleter(this);
        context.getPlugin().getCommand("report").setExecutor(new ReportCommand(this));
        setupPacketListeners();
        startReportCheck();
        startBpsCheck();
        startHistorySync();
        loadHistory();
    }

    public Global getContext() {
        return context;
    }

    private void loadConfig() {
        config = FileUtil.loadOrCreate(configFile, context.getPlugin(), "darkac.yml");
        config.addDefault("enabled", true);
        config.addDefault("alerts_enabled", true);
        config.addDefault("report_threshold", 2);
        config.addDefault("report_window_ms", 300000L);
        config.addDefault("ai_review.trigger_threshold", 5);
        config.addDefault("ai_review.auto_enabled", true);
        config.addDefault("ai_review.severe_speed_threshold", 15.0);
        config.addDefault("ai_review.severe_bps_threshold", 75.0);
        config.addDefault("alert.frequency", 3);
        config.addDefault("history_retention_days", 30);
        config.addDefault("detection.cooldown_ms", 2000L);
        config.options().copyDefaults(true);
        saveConfig();
        enabled = config.getBoolean("enabled", true);
        alertsEnabled = config.getBoolean("alerts_enabled", true);
    }

    private void saveConfig() {
        FileUtil.saveAsync(config, configFile, context.getPlugin());
    }

    private void initDetectors() {
        detectors.put(CheatType.FAST_HEAD_ROTATION, new HeadRotationDetector(config, this));
        detectors.put(CheatType.HIGH_CPS, new ClickSpeedDetector(config, this));
        detectors.put(CheatType.INVALID_MOVEMENT, new MovementDetector(config, this));
        detectors.put(CheatType.VERTICAL_TELEPORT, new VerticalTeleportDetector(config, this));
        detectors.put(CheatType.BLINK, new BlinkDetector(config, this));
        detectors.put(CheatType.KILLAURA, new KillAuraDetector(config, this));
        detectors.put(CheatType.REACH, new ReachDetector(config, this));
        detectors.put(CheatType.AUTO_CLICKER, new AutoClickerDetector(config, this));
        detectors.put(CheatType.FLY_HACK, new FlyHackDetector(config, this));
        detectors.put(CheatType.SPEED_HACK, new SpeedHackDetector(config, this));
    }

    private void setupPacketListeners() {
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.HIGH) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                Player player = (Player) event.getPlayer();
                if (player == null) return;
                UUID uuid = player.getUniqueId();
                PlayerCheatData data = cheatData.computeIfAbsent(uuid, k -> new PlayerCheatData());
                long now = System.currentTimeMillis();
                data.incrementPacketCount(event.getPacketType());
                data.packetTimestamps.add(now);
                if (!enabled) return;

                if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) {
                    WrapperPlayClientPlayerPosition position = new WrapperPlayClientPlayerPosition(event);
                    Vector3d pos = position.getPosition();
                    checkAllDetectors(player, data, now, pos.getX(), pos.getY(), pos.getZ());
                } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                    WrapperPlayClientPlayerPositionAndRotation posRot = new WrapperPlayClientPlayerPositionAndRotation(event);
                    Vector3d pos = posRot.getPosition();
                    checkAllDetectors(player, data, now, pos.getX(), pos.getY(), pos.getZ(), posRot.getYaw(), posRot.getPitch());
                } else if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
                    WrapperPlayClientEntityAction action = new WrapperPlayClientEntityAction(event);
                    if (action.getAction() == WrapperPlayClientEntityAction.Action.START_SNEAKING) {
                        data.lastHitTime = now;
                    }
                }
            }
        });
    }

    private void checkAllDetectors(Player player, PlayerCheatData data, long now, double x, double y, double z, float... rotation) {
        long cooldownMs = config.getLong("detection.cooldown_ms", 2000L);
        if (now - data.lastPacketTime < cooldownMs) return;

        detectors.values().forEach(detector -> detector.check(player, data, now, x, y, z));
        if (rotation.length == 2) detectors.get(CheatType.FAST_HEAD_ROTATION).check(player, data, now, rotation[0], rotation[1]);
        data.lastPacketTime = now;
    }

    private void loadHistory() {
        ConfigurationSection historySection = config.getConfigurationSection("cheat_history");
        if (historySection != null) {
            long retentionMs = config.getLong("history_retention_days", 30) * 24 * 60 * 60 * 1000L;
            long now = System.currentTimeMillis();
            for (String uuid : historySection.getKeys(false)) {
                List<Map<?, ?>> entries = (List<Map<?, ?>>) historySection.get(uuid);
                List<CheatHistoryEntry> history = new ArrayList<>();
                for (Map<?, ?> entry : entries) {
                    long timestamp = (long) entry.get("timestamp");
                    if (now - timestamp <= retentionMs) {
                        history.add(new CheatHistoryEntry(
                                CheatType.valueOf((String) entry.get("type")),
                                (String) entry.get("details"),
                                timestamp,
                                (String) entry.get("ai_response")
                        ));
                    }
                }
                cheatHistory.put(UUID.fromString(uuid), history);
            }
        }
    }

    private void saveHistory() {
        ConfigurationSection historySection = config.createSection("cheat_history");
        cheatHistory.forEach((uuid, entries) -> {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (CheatHistoryEntry entry : entries) {
                Map<String, Object> map = new HashMap<>();
                map.put("type", entry.type.name());
                map.put("details", entry.details);
                map.put("timestamp", entry.timestamp);
                map.put("ai_response", entry.aiResponse);
                serialized.add(map);
            }
            historySection.set(uuid.toString(), serialized);
        });
        saveConfig();
    }

    private void startHistorySync() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveHistory();
                //LogUtil.info("Anti-cheat history synced");
            }
        }.runTaskTimer(context.getPlugin(), 1200L, 1200L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        PlayerCheatData data = cheatData.computeIfAbsent(player.getUniqueId(), k -> new PlayerCheatData());
        long now = System.currentTimeMillis();
        checkAllDetectors(player, data, now, event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cheatData.remove(uuid);
        reports.remove(uuid);
        underAIReview.remove(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("darkpixel.admin")) {
            sender.sendMessage("§cNo permission!");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /darkac <toggle|alert|auto|threshold|status|review|history|ticket>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "toggle":
                enabled = !enabled;
                config.set("enabled", enabled);
                saveConfig();
                sender.sendMessage("§eDarkAC " + (enabled ? "enabled" : "disabled"));
                break;
            case "alert":
                alertsEnabled = !alertsEnabled;
                config.set("alerts_enabled", alertsEnabled);
                saveConfig();
                sender.sendMessage("§eAlerts " + (alertsEnabled ? "enabled" : "disabled"));
                break;
            case "auto":
                boolean autoReviewEnabled = config.getBoolean("ai_review.auto_enabled", true);
                autoReviewEnabled = !autoReviewEnabled;
                config.set("ai_review.auto_enabled", autoReviewEnabled);
                saveConfig();
                sender.sendMessage("§eAuto AI Review " + (autoReviewEnabled ? "enabled" : "disabled"));
                break;
            case "threshold":
                if (args.length != 2) {
                    sender.sendMessage("§cUsage: /darkac threshold <count>");
                    return true;
                }
                try {
                    int newThreshold = Integer.parseInt(args[1]);
                    if (newThreshold < 1) {
                        sender.sendMessage("§cThreshold must be at least 1!");
                        return true;
                    }
                    config.set("ai_review.trigger_threshold", newThreshold);
                    saveConfig();
                    sender.sendMessage("§eAI Review threshold set to " + newThreshold);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number: " + args[1]);
                }
                break;
            case "status":
                sender.sendMessage("§eDarkAC: " + (enabled ? "enabled" : "disabled") +
                        ", Alerts: " + (alertsEnabled ? "on" : "off") +
                        ", Auto Review: " + (config.getBoolean("ai_review.auto_enabled", true) ? "on" : "off") +
                        ", Threshold: " + config.getInt("ai_review.trigger_threshold", 5));
                break;
            case "review":
                if (args.length != 2) {
                    sender.sendMessage("§cUsage: /darkac review <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return true;
                }
                triggerAIReview(target, "MANUAL-" + System.currentTimeMillis(), sender);
                break;
            case "history":
                if (args.length != 2) {
                    sender.sendMessage("§cUsage: /darkac history <player>");
                    return true;
                }
                UUID histUuid = Bukkit.getPlayerUniqueId(args[1]);
                if (histUuid == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return true;
                }
                List<CheatHistoryEntry> history = cheatHistory.getOrDefault(histUuid, Collections.emptyList());
                sender.sendMessage("§eCheat History for " + args[1] + ":");
                history.forEach(entry -> sender.sendMessage(
                        String.format("§7[%s] %s: %s - AI: %s", new Date(entry.timestamp), entry.type, entry.details, entry.aiResponse)
                ));
                break;
            case "ticket":
                if (args.length != 2) {
                    sender.sendMessage("§cUsage: /darkac ticket <ticketId>");
                    return true;
                }
                showTicketDetails(sender, args[1]);
                break;
            default:
                sender.sendMessage("§cUnknown command!");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("darkpixel.admin")) return Collections.emptyList();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(Arrays.asList("toggle", "alert", "auto", "threshold", "status", "review", "history", "ticket"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "review":
                case "history":
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                case "ticket":
                    return new ArrayList<>(config.getConfigurationSection("report_tickets").getKeys(false));
                case "threshold":
                    return Arrays.asList("3", "5", "10", "15");
            }
        }
        return options.stream().filter(opt -> opt.startsWith(args[args.length - 1].toLowerCase())).collect(Collectors.toList());
    }

    private void showTicketDetails(CommandSender sender, String ticketId) {
        ConfigurationSection ticket = config.getConfigurationSection("report_tickets." + ticketId);
        if (ticket == null) {
            sender.sendMessage("§cTicket not found!");
            return;
        }
        sender.sendMessage("§eTicket " + ticketId + ":");
        sender.sendMessage("§7Target: " + ticket.getString("target"));
        sender.sendMessage("§7Reporter: " + ticket.getString("reporter"));
        sender.sendMessage("§7Time: " + new Date(ticket.getLong("timestamp")));
        sender.sendMessage("§7Reviewed: " + ticket.getBoolean("reviewed", false));
        sender.sendMessage("§7Status: " + ticket.getString("status"));
        if (ticket.contains("ai_response")) sender.sendMessage("§7AI Response: " + ticket.getString("ai_response"));
    }

    private void startReportCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                reports.entrySet().removeIf(entry -> {
                    entry.getValue().removeIf(r -> now - r.getTimestamp() > config.getLong("report_window_ms"));
                    return entry.getValue().isEmpty();
                });
            }
        }.runTaskTimer(context.getPlugin(), 0L, 200L);
    }

    private void startBpsCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerCheatData data = cheatData.getOrDefault(player.getUniqueId(), new PlayerCheatData());
                    double bps = data.getAverageBps();
                    double severeBpsThreshold = config.getDouble("ai_review.severe_bps_threshold", 75.0);
                    if (bps > severeBpsThreshold) {
                        triggerAlert(player, CheatType.BLINK, "BPS: " + String.format("%.2f", bps));
                    }
                }
            }
        }.runTaskTimer(context.getPlugin(), 0L, 60L);
    }

    public void triggerAlert(Player player, CheatType type, String details) {
        if (!enabled) return;

        UUID uuid = player.getUniqueId();
        PlayerCheatData data = cheatData.get(uuid);
        if (data == null) return;

        long now = System.currentTimeMillis();
        long cooldownMs = config.getLong("detection.cooldown_ms", 2000L);
        if (now - data.lastAlertTime < cooldownMs) return;

        Map<CheatType, Integer> counts = triggerCounts.computeIfAbsent(uuid, k -> new HashMap<>());
        int violationCount = counts.merge(type, 1, Integer::sum);

        data.lastAlertTime = now;

        if (!alertsEnabled) {
            logCheat(player, type, details, "Alerts disabled");
            return;
        }

        int aiReviewThreshold = config.getInt("ai_review.trigger_threshold", 5);
        int alertFrequency = config.getInt("alert.frequency", 3);
        boolean autoReviewEnabled = config.getBoolean("ai_review.auto_enabled", true);

        if (violationCount % alertFrequency == 0) {
            String alertMessage = String.format("[DarkAC] %s triggered %s: %s (x%d)",
                    player.getName(), type, details, violationCount);
            Bukkit.getOnlinePlayers().stream()
                    .filter(Player::isOp)
                    .forEach(op -> op.sendMessage("§e" + alertMessage));
            LogUtil.warning(alertMessage);
            logCheat(player, type, details, "Pending review");
        }

        if (autoReviewEnabled && violationCount >= aiReviewThreshold && !underAIReview.getOrDefault(uuid, false)) {
            String ticketId = String.format("AUTO-%s-%d", type.name(), System.currentTimeMillis());
            String reviewMessage = String.format("[DarkAC] %s triggered %s: %s (x%d) - Starting AI Review (%s)",
                    player.getName(), type, details, violationCount, ticketId);
            Bukkit.getOnlinePlayers().stream()
                    .filter(Player::isOp)
                    .forEach(op -> op.sendMessage("§e" + reviewMessage));
            LogUtil.warning(reviewMessage);
            triggerAIReview(player, ticketId, null);
            counts.put(type, 0);
        }
    }

    private CompletableFuture<String> aiAnalyzeCheat(Player player, CheatType type, String details, int count) {
        double severeSpeedThreshold = config.getDouble("ai_review.severe_speed_threshold", 15.0);
        double severeBpsThreshold = config.getDouble("ai_review.severe_bps_threshold", 75.0);
        PlayerCheatData data = cheatData.getOrDefault(player.getUniqueId(), new PlayerCheatData());
        double currentBps = data.getAverageBps();

        // 增加玩家的上下文信息
        String playerContext = String.format(
                "游戏模式: %s, 飞行: %s, 效果: %s",
                player.getGameMode(), player.isFlying() ? "是" : "否",
                player.getActivePotionEffects().stream()
                        .map(e -> e.getType().getName() + " " + e.getAmplifier())
                        .collect(Collectors.joining(", "))
        );

        String prompt = String.format(
                "你是Minecraft服务器的反作弊AI分析助手。请根据以下信息判断玩家是否作弊，并返回 '/ban <player> AI: <理由>' 或 '/monitor <player> AI: <理由>'。\n" +
                        "玩家: %s\n作弊类型: %s\n详情: %s\n触发次数: %d\n当前BPS: %.2f (严重阈值: %.2f)\n最近10条历史记录:\n%s\n玩家上下文: %s",
                player.getName(), type, details, count, currentBps, severeBpsThreshold, getHistorySummary(player.getUniqueId()), playerContext
        );
        aiChat.sendMessage(player, prompt, false);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1000); // 模拟AI处理延迟
                int threshold = config.getInt("ai_review.trigger_threshold", 5);
                boolean isSevere = count >= threshold * 2 || currentBps > severeBpsThreshold;
                if (isSevere) {
                    return "/ban " + player.getName() + " AI: Repeated severe violations or excessive BPS detected";
                }
                return "/monitor " + player.getName() + " AI: Suspicious activity detected, further monitoring required";
            } catch (InterruptedException e) {
                return "/monitor " + player.getName() + " AI: Analysis interrupted";
            }
        });
    }

    private String getHistorySummary(UUID uuid) {
        List<CheatHistoryEntry> history = cheatHistory.getOrDefault(uuid, Collections.emptyList());
        return history.stream()
                .sorted((e1, e2) -> Long.compare(e2.timestamp, e1.timestamp))
                .limit(10)
                .map(e -> String.format("%s: %s (%s)", e.type, e.details, new Date(e.timestamp)))
                .collect(Collectors.joining("\n"));
    }

    private void logCheat(Player player, CheatType type, String details, String aiResponse) {
        UUID uuid = player.getUniqueId();
        List<CheatHistoryEntry> history = cheatHistory.computeIfAbsent(uuid, k -> new ArrayList<>());
        CheatHistoryEntry entry = new CheatHistoryEntry(type, details, System.currentTimeMillis(), aiResponse);
        history.add(entry);
        if (aiResponse.contains("/ban")) {
            // 添加管理员审核步骤
            Bukkit.getOnlinePlayers().stream()
                    .filter(Player::isOp)
                    .forEach(op -> op.sendMessage("§cAI建议禁封 " + player.getName() + "，理由: " + aiResponse + "，请确认"));
        }
    }

    public void addReport(Player target, Player reporter) {
        UUID targetUUID = target.getUniqueId();
        List<Report> targetReports = reports.computeIfAbsent(targetUUID, k -> new ArrayList<>());
        targetReports.add(new Report(reporter.getUniqueId(), System.currentTimeMillis()));
        String ticketId = String.format("T%06d", ticketCounter++);
        saveReportTicket(ticketId, targetUUID, reporter.getUniqueId(), false);
        Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOp)
                .forEach(op -> op.sendMessage("§eNew report ticket " + ticketId + ": " + reporter.getName() + " reported " + target.getName()));

        int reportThreshold = config.getInt("report_threshold", 2);
        if (targetReports.size() >= reportThreshold) {
            triggerAIReview(target, "REPORT-" + ticketId, null);
        }
    }

    private void saveReportTicket(String ticketId, UUID target, UUID reporter, boolean reviewed) {
        Map<String, Object> ticket = new HashMap<>();
        ticket.put("target", target.toString());
        ticket.put("reporter", reporter.toString());
        ticket.put("timestamp", System.currentTimeMillis());
        ticket.put("reviewed", reviewed);
        ticket.put("status", "pending");
        config.set("report_tickets." + ticketId, ticket);
        reportTickets.put(ticketId, new ReportTicket(target, reporter));
        saveConfig();
    }

    private void triggerAIReview(Player player, String ticketId, CommandSender initiator) {
        UUID uuid = player.getUniqueId();
        if (underAIReview.getOrDefault(uuid, false)) {
            LogUtil.info("AI review already in progress for " + player.getName());
            return;
        }
        underAIReview.put(uuid, true);
        String message = "§cAI Review triggered for " + player.getName() + " (Ticket: " + ticketId + ")";
        if (initiator != null) initiator.sendMessage(message);
        else Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage(message));

        Map<CheatType, Integer> counts = triggerCounts.getOrDefault(uuid, Collections.emptyMap());
        int totalViolations = counts.values().stream().mapToInt(Integer::intValue).sum();

        aiAnalyzeCheat(player, CheatType.REPORT, "Automated review triggered", totalViolations)
                .thenAccept(response -> {
                    ConfigurationSection ticket = config.getConfigurationSection("report_tickets." + ticketId);
                    if (ticket != null) {
                        ticket.set("reviewed", true);
                        ticket.set("status", response.contains("/ban") ? "BANNED" : "MONITORED");
                        ticket.set("ai_response", response);
                        ticket.set("review_timestamp", System.currentTimeMillis());
                    }
                    saveConfig();

                    logCheat(player, CheatType.REPORT, "Reviewed by AI (Ticket: " + ticketId + ")", response);
                    String result = "§eAI Review Result for " + player.getName() + ": " + response;
                    if (initiator != null) initiator.sendMessage(result);
                    else Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage(result));
                    underAIReview.remove(uuid);
                });
    }

    static class CheatHistoryEntry {
        final CheatType type;
        final String details;
        final long timestamp;
        final String aiResponse;

        CheatHistoryEntry(CheatType type, String details, long timestamp, String aiResponse) {
            this.type = type;
            this.details = details;
            this.timestamp = timestamp;
            this.aiResponse = aiResponse;
        }
    }

    static class Report {
        private final UUID reporter;
        private final long timestamp;

        Report(UUID reporter, long timestamp) {
            this.reporter = reporter;
            this.timestamp = timestamp;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    static class ReportTicket {
        final UUID target;
        final UUID reporter;

        ReportTicket(UUID target, UUID reporter) {
            this.target = target;
            this.reporter = reporter;
        }
    }

    public enum CheatType {
        FAST_HEAD_ROTATION, HIGH_CPS, INVALID_MOVEMENT, VERTICAL_TELEPORT, BLINK,
        KILLAURA, REACH, AUTO_CLICKER, FLY_HACK, SPEED_HACK, REPORT
    }
}