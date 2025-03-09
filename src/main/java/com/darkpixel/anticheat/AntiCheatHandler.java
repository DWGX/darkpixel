package com.darkpixel.anticheat;

import com.darkpixel.Global;
import com.darkpixel.ai.AiChatHandler;
import com.darkpixel.utils.FileUtil;
import com.darkpixel.utils.PlayerData;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import com.darkpixel.anticheat.detectors.*; // 正确导入探测器包

public class AntiCheatHandler implements Listener, CommandExecutor, TabCompleter {
    private final Global context;
    private final AiChatHandler aiChat;
    private final PlayerData playerData;
    private final File configFile;
    private YamlConfiguration config;
    private boolean enabled;
    private boolean alertsEnabled = true;
    public final Map<UUID, PlayerCheatData> cheatData = new ConcurrentHashMap<>();
    private final Map<UUID, List<Report>> reports = new ConcurrentHashMap<>();
    private final Map<UUID, Map<CheatType, Integer>> triggerCounts = new ConcurrentHashMap<>();
    private final Map<CheatType, Detector> detectors = new EnumMap<>(CheatType.class);
    private final Map<String, ReportTicket> reportTickets = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> underAIReview = new ConcurrentHashMap<>();
    private final Map<UUID, List<CheatHistoryEntry>> cheatHistory = new ConcurrentHashMap<>();
    private final Map<UUID, Double> dynamicThresholds = new ConcurrentHashMap<>();
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
        enabled = config.getBoolean("enabled", true);
        alertsEnabled = config.getBoolean("alerts_enabled", true);
        saveConfig();
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

    private void checkAllDetectors(Player player, PlayerCheatData data, long timestamp, double x, double y, double z, float... rotation) {
        long cooldownMs = config.getLong("detection.cooldown_ms", 3000L);
        if (timestamp - data.lastPacketTime < cooldownMs) return;

        double dynamicThreshold = dynamicThresholds.computeIfAbsent(player.getUniqueId(), k -> 1.0);
        data.updateBehaviorMetrics(x, y, z, timestamp);
        detectors.values().forEach(detector -> {
            detector.setDynamicThreshold(dynamicThreshold);
            detector.check(player, data, timestamp, x, y, z);
        });
        if (rotation.length == 2) {
            detectors.get(CheatType.FAST_HEAD_ROTATION).setDynamicThreshold(dynamicThreshold);
            detectors.get(CheatType.FAST_HEAD_ROTATION).check(player, data, timestamp, rotation[0], rotation[1]);
        }
        data.lastPacketTime = timestamp;
        adjustDynamicThreshold(player, data);
    }

    private void adjustDynamicThreshold(Player player, PlayerCheatData data) {
        double avgMoveSpeed = data.getAverageMoveSpeed();
        double avgCps = data.getAverageCps();
        double baseThreshold = 1.0;
        if (avgMoveSpeed > 0.5 || avgCps > 15) {
            baseThreshold *= 0.8;
        } else if (playerData.getPlayerInfo(player.getName()).loginCount > 100) {
            baseThreshold *= 1.2;
        }
        dynamicThresholds.put(player.getUniqueId(), Math.max(0.5, Math.min(2.0, baseThreshold)));
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
        dynamicThresholds.remove(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("darkpixel.admin")) {
            sender.sendMessage("§c没权限！");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§c用法: /darkac <toggle|alert|auto|threshold|status|review|history|ticket>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "toggle":
                enabled = !enabled;
                config.set("enabled", enabled);
                saveConfig();
                sender.sendMessage("§eDarkAC " + (enabled ? "已启用" : "已禁用"));
                break;
            case "alert":
                alertsEnabled = !alertsEnabled;
                config.set("alerts_enabled", alertsEnabled);
                saveConfig();
                sender.sendMessage("§e警报 " + (alertsEnabled ? "已开" : "已关"));
                break;
            case "auto":
                boolean autoReviewEnabled = config.getBoolean("ai_review.auto_enabled", true);
                autoReviewEnabled = !autoReviewEnabled;
                config.set("ai_review.auto_enabled", autoReviewEnabled);
                saveConfig();
                sender.sendMessage("§e自动AI审查 " + (autoReviewEnabled ? "已开" : "已关"));
                break;
            case "threshold":
                if (args.length != 2) {
                    sender.sendMessage("§c用法: /darkac threshold <次数>");
                    return true;
                }
                int newThreshold = Integer.parseInt(args[1]);
                config.set("ai_review.trigger_threshold", newThreshold);
                saveConfig();
                sender.sendMessage("§eAI审查阈值设为 " + newThreshold);
                break;
            case "status":
                sender.sendMessage("§eDarkAC: " + (enabled ? "启用" : "禁用") +
                        ", 警报: " + (alertsEnabled ? "开" : "关") +
                        ", 自动审查: " + (config.getBoolean("ai_review.auto_enabled", true) ? "开" : "关") +
                        ", 阈值: " + config.getInt("ai_review.trigger_threshold", 5));
                break;
            case "review":
                if (args.length != 2) {
                    sender.sendMessage("§c用法: /darkac review <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家没找到！");
                    return true;
                }
                underAIReview.put(target.getUniqueId(), true);
                aiChat.sendMessage(target, "Reviewing potential cheating behavior for " + target.getName(), false);
                sender.sendMessage("§e已触发对 " + target.getName() + " 的AI审查");
                break;
            case "history":
                if (args.length != 2) {
                    sender.sendMessage("§c用法: /darkac history <玩家>");
                    return true;
                }
                Player historyTarget = Bukkit.getPlayer(args[1]);
                if (historyTarget == null) {
                    sender.sendMessage("§c玩家没找到！");
                    return true;
                }
                List<CheatHistoryEntry> history = cheatHistory.getOrDefault(historyTarget.getUniqueId(), new ArrayList<>());
                if (history.isEmpty()) {
                    sender.sendMessage("§e玩家 " + historyTarget.getName() + " 无作弊记录");
                } else {
                    history.forEach(entry -> sender.sendMessage("§e[" + entry.timestamp + "] " + entry.type.getName() + ": " + entry.details));
                }
                break;
            default:
                sender.sendMessage("§c用法: /darkac <toggle|alert|auto|threshold|status|review|history|ticket>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.addAll(Arrays.asList("toggle", "alert", "auto", "threshold", "status", "review", "history", "ticket"));
        } else if (args.length == 2 && Arrays.asList("review", "history").contains(args[0].toLowerCase())) {
            Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
        }
        return suggestions;
    }

    private void startReportCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                reports.forEach((uuid, reportList) -> {
                    if (reportList.size() >= config.getInt("report_threshold", 3)) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && !underAIReview.getOrDefault(uuid, false)) {
                            underAIReview.put(uuid, true);
                            aiChat.sendMessage(player, "Multiple reports received, reviewing behavior for " + player.getName(), false);
                        }
                    }
                });
            }
        }.runTaskTimer(context.getPlugin(), 1200L, 1200L);
    }

    private void startBpsCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cheatData.forEach((uuid, data) -> {
                    data.cleanupOldTimestamps();
                });
            }
        }.runTaskTimer(context.getPlugin(), 1200L, 1200L);
    }

    public void addReport(Player target, Player reporter) {
        List<Report> targetReports = reports.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>());
        targetReports.add(new Report(reporter.getName(), "Suspected cheating", System.currentTimeMillis()));
    }

    public void triggerAlert(Player player, CheatType type, String details) {
        if (!alertsEnabled) return;
        UUID uuid = player.getUniqueId();
        Map<CheatType, Integer> playerTriggers = triggerCounts.computeIfAbsent(uuid, k -> new HashMap<>());
        int count = playerTriggers.merge(type, 1, Integer::sum);
        long now = System.currentTimeMillis();

        if (now - cheatData.get(uuid).lastAlertTime > 5000) {
            String message = "§c[DarkAC] " + player.getName() + " 疑似使用 " + type.getName() + ": " + details + " (触发次数: " + count + ")";
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("darkpixel.admin"))
                    .forEach(p -> p.sendMessage(message));
            cheatHistory.computeIfAbsent(uuid, k -> new ArrayList<>())
                    .add(new CheatHistoryEntry(type, details, now, "Pending AI review"));
            cheatData.get(uuid).lastAlertTime = now;

            int threshold = config.getInt("ai_review.trigger_threshold", 5);
            if (count >= threshold && !underAIReview.getOrDefault(uuid, false)) {
                underAIReview.put(uuid, true);
                aiChat.sendMessage(player, "Behavior review triggered for " + player.getName() + " due to " + type.getName(), false);
            }
        }
    }

    public enum CheatType {
        FAST_HEAD_ROTATION("Fast Head Rotation"),
        HIGH_CPS("High Click Speed"),
        INVALID_MOVEMENT("Invalid Movement"),
        VERTICAL_TELEPORT("Vertical Teleport"),
        BLINK("Blink Detected"),
        KILLAURA("KillAura Detected"),
        REACH("Reach Hack Detected"),
        AUTO_CLICKER("Auto Clicker Detected"),
        FLY_HACK("Fly Hack Detected"),
        SPEED_HACK("Speed Hack Detected");

        private final String name;

        CheatType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public interface Detector {
        void check(Player player, PlayerCheatData data, long timestamp, double x, double y, double z);
        void check(Player player, PlayerCheatData data, long timestamp, float yaw, float pitch);
        void setDynamicThreshold(double threshold);
    }

    public static class PlayerCheatData {
        public Float lastYaw = null;
        public Float lastPitch = null;
        public Location lastLocation = null;
        public long lastPacketTime = 0;
        public long lastMoveTime = 0;
        public long lastHitTime = 0;
        public long lastAlertTime = 0;
        public int blinkCount = 0;
        public ConcurrentLinkedDeque<Long> clickTimes = new ConcurrentLinkedDeque<>();
        public ConcurrentLinkedDeque<Long> packetTimestamps = new ConcurrentLinkedDeque<>();
        public Map<PacketTypeCommon, Integer> packetCounts = new HashMap<>();
        public int totalPackets = 0;
        public int anomalyCount = 0;
        public long lastGroundTime = 0;
        public double lastVerticalSpeed = 0.0;
        public List<Double> moveSpeeds = Collections.synchronizedList(new ArrayList<>());
        public List<Long> clickTimestamps = Collections.synchronizedList(new ArrayList<>());

        public void incrementPacketCount(PacketTypeCommon type) {
            packetCounts.merge(type, 1, Integer::sum);
            totalPackets++;
        }

        public double getAverageBps() {
            long now = System.currentTimeMillis();
            packetTimestamps.removeIf(t -> now - t > 5000);
            return packetTimestamps.isEmpty() ? 0.0 : packetTimestamps.size() / 5.0;
        }

        public double getPeakBps() {
            long now = System.currentTimeMillis();
            Map<Long, Integer> secondCounts = new HashMap<>();
            for (long timestamp : packetTimestamps) {
                long second = (now - timestamp) / 1000;
                secondCounts.merge(second, 1, Integer::sum);
            }
            return secondCounts.values().stream().max(Integer::compare).orElse(0);
        }

        public void cleanupOldTimestamps() {
            long now = System.currentTimeMillis();
            packetTimestamps.removeIf(ts -> now - ts > 60000);
            clickTimestamps.removeIf(ts -> now - ts > 60000);
            moveSpeeds.removeIf(speed -> moveSpeeds.indexOf(speed) < moveSpeeds.size() - 100);
        }

        public void updateBehaviorMetrics(double x, double y, double z, long timestamp) {
            if (lastMoveTime > 0 && lastLocation != null) {
                double distance = Math.sqrt(Math.pow(x - lastLocation.getX(), 2) + Math.pow(y - lastLocation.getY(), 2) + Math.pow(z - lastLocation.getZ(), 2));
                double timeDiff = (timestamp - lastMoveTime) / 1000.0;
                if (timeDiff > 0) {
                    double speed = distance / timeDiff;
                    moveSpeeds.add(speed);
                }
            }
            lastMoveTime = timestamp;
            lastLocation = new Location(Bukkit.getWorlds().get(0), x, y, z);
        }

        public double getAverageMoveSpeed() {
            if (moveSpeeds.isEmpty()) return 0.0;
            return moveSpeeds.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getAverageCps() {
            if (clickTimestamps.isEmpty()) return 0.0;
            long now = System.currentTimeMillis();
            long window = 1000L;
            long count = clickTimestamps.stream().filter(ts -> now - ts <= window).count();
            return count;
        }
    }

    public static class ReportCommand implements CommandExecutor {
        private final AntiCheatHandler handler;
        private final Map<UUID, Long> cooldowns = new HashMap<>();
        private static final long COOLDOWN_TIME = 30000L;

        public ReportCommand(AntiCheatHandler handler) {
            this.handler = handler;
            startCooldownCleanup();
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c仅玩家可使用此命令！");
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage("§c用法: /report <玩家名>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§c玩家 " + args[0] + " 不在线！");
                return true;
            }
            if (target.equals(player)) {
                sender.sendMessage("§c不能举报自己！");
                return true;
            }
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            if (cooldowns.containsKey(uuid) && (now - cooldowns.get(uuid)) < COOLDOWN_TIME) {
                long remaining = (COOLDOWN_TIME - (now - cooldowns.get(uuid))) / 1000;
                player.sendMessage("§c请等待 " + remaining + " 秒后再举报！");
                return true;
            }
            cooldowns.put(uuid, now);
            handler.addReport(target, player);
            player.sendMessage("§a已举报 " + target.getName() + "！若多人举报，将触发AI审查。");
            notifyAdmins(player, target);
            return true;
        }

        private void notifyAdmins(Player reporter, Player target) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player admin : Bukkit.getOnlinePlayers()) {
                        if (admin.hasPermission("darkpixel.admin") && !admin.equals(reporter)) {
                            admin.sendMessage("§e" + reporter.getName() + " 举报了 " + target.getName() + "，请注意观察。");
                        }
                    }
                }
            }.runTaskAsynchronously(handler.getContext().getPlugin());
        }

        private void startCooldownCleanup() {
            new BukkitRunnable() {
                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    cooldowns.entrySet().removeIf(entry -> (now - entry.getValue()) >= COOLDOWN_TIME);
                }
            }.runTaskTimer(handler.getContext().getPlugin(), 0L, 600L);
        }
    }

    public static class CheatHistoryEntry {
        CheatType type;
        String details;
        long timestamp;
        String aiResponse;

        CheatHistoryEntry(CheatType type, String details, long timestamp, String aiResponse) {
            this.type = type;
            this.details = details;
            this.timestamp = timestamp;
            this.aiResponse = aiResponse;
        }
    }

    public static class Report {
        String reporter;
        String reason;
        long timestamp;

        Report(String reporter, String reason, long timestamp) {
            this.reporter = reporter;
            this.reason = reason;
            this.timestamp = timestamp;
        }
    }

    public static class ReportTicket {
        String ticketId;
        UUID playerId;
        String reason;

        ReportTicket(String ticketId, UUID playerId, String reason) {
            this.ticketId = ticketId;
            this.playerId = playerId;
            this.reason = reason;
        }
    }
}