package com.darkpixel.anticheat;

import com.darkpixel.Global;
import com.darkpixel.ai.AiChatHandler;
import com.darkpixel.anticheat.detectors.AbstractCheckDetector;
import com.darkpixel.anticheat.detectors.CheckAimDetector;
import com.darkpixel.anticheat.detectors.HeadRotationDetector; // 新增导入
import com.darkpixel.manager.BanManager;
import com.darkpixel.utils.FileUtil;
import com.darkpixel.utils.LogUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.handshaking.client.WrapperHandshakingClientHandshake;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AntiCheatHandler implements Listener, CommandExecutor, TabCompleter {
    private final Global context;
    private final AiChatHandler aiChat;
    private final BanManager banManager;
    private final File configFile;
    private YamlConfiguration config;
    private boolean enabled;
    private boolean alertsEnabled;
    private final Map<UUID, PlayerCheatData> cheatData = new ConcurrentHashMap<>();
    private final Map<UUID, Map<CheatType, Integer>> triggerCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> vlPerMinute = new ConcurrentHashMap<>();
    private final Map<UUID, String> clientIdentifiers = new ConcurrentHashMap<>();
    private final Map<CheatType, Detector> detectors = new EnumMap<>(CheatType.class);
    private final Map<UUID, CheckPlayer> checkPlayers = new ConcurrentHashMap<>();

    public AntiCheatHandler(Global context) {
        this.context = context;
        this.aiChat = context.getAiChat();
        this.banManager = new BanManager(context);
        this.configFile = new File(context.getPlugin().getDataFolder(), "darkac.yml");
        loadConfig();
        initDetectors();
        context.getPlugin().getServer().getPluginManager().registerEvents(this, context.getPlugin());
        context.getPlugin().getCommand("darkac").setExecutor(this);
        context.getPlugin().getCommand("darkac").setTabCompleter(this);
        context.getPlugin().getCommand("report").setExecutor(new ReportCommand(this));
        setupPacketListeners();
        startVLCheck();
    }

    public Global getContext() {
        return context;
    }

    private void loadConfig() {
        config = FileUtil.loadOrCreate(configFile, context.getPlugin(), "darkac.yml");
        config.addDefault("enabled", true);
        config.addDefault("alerts_enabled", true);
        config.addDefault("ai_review.trigger_threshold", 3);
        config.addDefault("ai_review.auto_enabled", true);
        config.addDefault("vl_threshold", 20);
        config.addDefault("detection.cooldown_ms", 1000L);
        config.addDefault("detectors.head_rotation.max_angle", 90.0); // 新增配置项
        config.addDefault("detectors.head_rotation.min_time_ms", 50); // 新增配置项
        config.options().copyDefaults(true);
        FileUtil.saveAsync(config, configFile, context.getPlugin());
        enabled = config.getBoolean("enabled", true);
        alertsEnabled = config.getBoolean("alerts_enabled", true);
    }

    private void initDetectors() {
        detectors.put(CheatType.AIM, new CheckAimDetector(config, this, checkPlayers));
        detectors.put(CheatType.FAST_HEAD_ROTATION, new HeadRotationDetector(config, this, checkPlayers)); // 注册新检测器
    }

    private void setupPacketListeners() {
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.HIGH) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                Player player = (Player) event.getPlayer();
                if (event.getPacketType() == PacketType.Handshaking.Client.HANDSHAKE) {
                    WrapperHandshakingClientHandshake handshake = new WrapperHandshakingClientHandshake(event);
                    String identifierData = player != null ? player.getUniqueId().toString() + "|" +
                            player.getAddress().getAddress().getHostAddress() + "|" +
                            handshake.getProtocolVersion() : "unknown";
                    String identifier = generateClientIdentifier(identifierData);
                    if (player != null) clientIdentifiers.put(player.getUniqueId(), identifier);
                } else if (event.getPacketType() == PacketType.Login.Client.LOGIN_START) {
                    WrapperLoginClientLoginStart loginStart = new WrapperLoginClientLoginStart(event);
                    String identifierData = loginStart.getUsername() + "|" +
                            (player != null ? player.getAddress().getAddress().getHostAddress() : "unknown");
                    String identifier = generateClientIdentifier(identifierData);
                    if (player != null) clientIdentifiers.put(player.getUniqueId(), identifier);
                }

                if (player == null || !enabled) return;
                UUID uuid = player.getUniqueId();
                PlayerCheatData data = cheatData.computeIfAbsent(uuid, k -> new PlayerCheatData());
                CheckPlayer checkPlayer = checkPlayers.computeIfAbsent(uuid, k -> new CheckPlayer(player));
                long now = System.currentTimeMillis();
                data.incrementPacketCount(event.getPacketType());
                data.packetTimestamps.add(now);

                checkPlayer.update(event);

                detectors.values().forEach(detector -> {
                    if (detector instanceof AbstractCheckDetector) {
                        ((AbstractCheckDetector) detector).onPacketReceive(event);
                    }
                });

                if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) {
                    WrapperPlayClientPlayerPosition position = new WrapperPlayClientPlayerPosition(event);
                    Vector3d pos = position.getPosition();
                    checkAllDetectors(player, data, now, pos.getX(), pos.getY(), pos.getZ());
                } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                    WrapperPlayClientPlayerPositionAndRotation posRot = new WrapperPlayClientPlayerPositionAndRotation(event);
                    Vector3d pos = posRot.getPosition();
                    checkAllDetectors(player, data, now, pos.getX(), pos.getY(), pos.getZ(), posRot.getYaw(), posRot.getPitch());
                }
            }
        });
    }

    private String generateClientIdentifier(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return UUID.randomUUID().toString();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String identifier = clientIdentifiers.get(player.getUniqueId());
        if (identifier != null && isBlacklisted(identifier)) {
            player.kickPlayer("你的设备已被拉黑，请联系管理员！");
        }
    }

    private boolean isBlacklisted(String identifier) {
        try (Connection conn = context.getRankManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM blacklist WHERE identifier = ?")) {
            ps.setString(1, identifier);
            var rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void checkAllDetectors(Player player, PlayerCheatData data, long now, double x, double y, double z, float... rotation) {
        long cooldownMs = config.getLong("detection.cooldown_ms", 1000L);
        if (now - data.lastPacketTime < cooldownMs) return;

        detectors.values().forEach(detector -> detector.check(player, data, now, x, y, z));
        data.lastPacketTime = now;
    }

    private void startVLCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;
                int vlThreshold = config.getInt("vl_threshold", 20);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    int vl = vlPerMinute.getOrDefault(uuid, 0);
                    if (vl > vlThreshold) {
                        String reason = String.format("超过每分钟违规阈值 (%d VL)，详细记录: %s", vl, getViolationDetails(uuid));
                        String identifier = clientIdentifiers.get(uuid);
                        banManager.banPlayer(player.getName(), -1, reason, identifier);
                        LogUtil.warning("玩家 " + player.getName() + " 被自动封禁: " + reason);
                    }
                    vlPerMinute.put(uuid, 0);
                }
            }
        }.runTaskTimer(context.getPlugin(), 0L, 1200L);
    }

    public void triggerAlert(Player player, CheatType type, String details) {
        if (!enabled) return;
        UUID uuid = player.getUniqueId();
        PlayerCheatData data = cheatData.get(uuid);
        if (data == null) return;

        long now = System.currentTimeMillis();
        long cooldownMs = config.getLong("detection.cooldown_ms", 1000L);
        if (now - data.lastAlertTime < cooldownMs) return;

        Map<CheatType, Integer> counts = triggerCounts.computeIfAbsent(uuid, k -> new HashMap<>());
        int violationCount = counts.merge(type, 1, Integer::sum);
        vlPerMinute.merge(uuid, 1, Integer::sum);
        data.lastAlertTime = now;

        if (alertsEnabled) {
            String alertMessage = String.format("[DarkAC] %s triggered %s: %s (x%d)", player.getName(), type, details, violationCount);
            Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage("§e" + alertMessage));
            LogUtil.warning(alertMessage);
        }

        int aiReviewThreshold = config.getInt("ai_review.trigger_threshold", 3);
        boolean autoReviewEnabled = config.getBoolean("ai_review.auto_enabled", true);
        if (autoReviewEnabled && violationCount >= aiReviewThreshold) {
            triggerAIReview(player, type, violationCount, details);
            counts.put(type, 0);
        }
    }

    private String getViolationDetails(UUID uuid) {
        Map<CheatType, Integer> counts = triggerCounts.getOrDefault(uuid, Collections.emptyMap());
        return counts.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private void triggerAIReview(Player player, CheatType type, int count, String details) {
        String ticketId = "AUTO-" + type.name() + "-" + System.currentTimeMillis();
        aiChat.sendMessage(player, String.format(
                "玩家 %s 触发 %s 检测，次数: %d，详情: %s\n请判断是否作弊，返回 '/ban %s AI: %s' 或 '/monitor %s AI: 可疑行为'",
                player.getName(), type, count, details, player.getName(), type, player.getName()
        ), false, response -> {
            Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage("§eAI Review: " + response));
            if (response.startsWith("/ban")) {
                String reason = response.split("AI: ")[1];
                String identifier = clientIdentifiers.get(player.getUniqueId());
                banManager.banPlayer(player.getName(), -1, reason, identifier);
            }
        });
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
        triggerCounts.remove(uuid);
        vlPerMinute.remove(uuid);
        clientIdentifiers.remove(uuid);
        checkPlayers.remove(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("darkpixel.admin")) {
            sender.sendMessage("§cNo permission!");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /darkac <toggle|alert|status>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "toggle":
                enabled = !enabled;
                config.set("enabled", enabled);
                FileUtil.saveAsync(config, configFile, context.getPlugin());
                sender.sendMessage("§eDarkAC " + (enabled ? "enabled" : "disabled"));
                break;
            case "alert":
                alertsEnabled = !alertsEnabled;
                config.set("alerts_enabled", alertsEnabled);
                FileUtil.saveAsync(config, configFile, context.getPlugin());
                sender.sendMessage("§eAlerts " + (alertsEnabled ? "enabled" : "disabled"));
                break;
            case "status":
                sender.sendMessage("§eDarkAC: " + (enabled ? "enabled" : "disabled") +
                        ", Alerts: " + (alertsEnabled ? "on" : "off"));
                break;
            default:
                sender.sendMessage("§cUnknown command!");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("darkpixel.admin")) return Collections.emptyList();
        if (args.length == 1) {
            return Arrays.asList("toggle", "alert", "status").stream()
                    .filter(opt -> opt.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public void addReport(Player target, Player reporter) {
        triggerAlert(target, CheatType.REPORT, "Reported by " + reporter.getName());
    }

    public enum CheatType {
        AIM("Aim Hack"),
        FAST_HEAD_ROTATION("Fast Head Rotation"), // 新增枚举值
        REPORT("Player Reported");

        private final String name;

        CheatType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}