package com.darkpixel.anticheat;

import com.darkpixel.Global;
import com.darkpixel.anticheat.detectors.*;
import com.darkpixel.utils.FileUtil;
import com.darkpixel.utils.LogUtil;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCheatHandler implements Listener, CommandExecutor {
    private final Global context;
    private final PlayerData playerData;
    private final File configFile;
    private YamlConfiguration config;
    private boolean enabled;
    private final Map<UUID, PlayerCheatData> cheatData = new ConcurrentHashMap<>();
    private final Map<UUID, List<Report>> reports = new ConcurrentHashMap<>();
    private final Map<CheatType, Detector> detectors = new EnumMap<>(CheatType.class);
    private final double maxBps;
    private final Map<UUID, InteractData> interactData = new ConcurrentHashMap<>();
    private final Map<UUID, Map<CheatType, Integer>> triggerCounts = new ConcurrentHashMap<>();

    public Global getContext() {
        return context;
    }

    public AntiCheatHandler(Global context) {
        this.context = context;
        this.playerData = context.getPlayerData();
        this.configFile = new File(context.getPlugin().getDataFolder(), "darkac.yml");
        loadConfig();
        this.maxBps = config.getDouble("detectors.blink.max_bps", 50.0);
        initDetectors();
        context.getPlugin().getServer().getPluginManager().registerEvents(this, context.getPlugin());
        context.getPlugin().getCommand("darkac").setExecutor(this);
        context.getPlugin().getCommand("report").setExecutor(new ReportCommand(this));
        setupPacketListeners();
        startReportCheck();
        startBpsCheck();
        startInteractCheck();
        startCleanupTask();
    }

    private void loadConfig() {
        config = FileUtil.loadOrCreate(configFile, context.getPlugin(), "darkac.yml");
        config.addDefault("enabled", false);
        config.addDefault("report_threshold", 2);
        config.addDefault("report_window_ms", 300000L);
        if (!config.isConfigurationSection("alert_prefs")) {
            config.createSection("alert_prefs", new HashMap<String, Boolean>());
        }
        config.options().copyDefaults(true);
        saveConfig();
        enabled = config.getBoolean("enabled", false);
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
                data.incrementPacketCount((PacketTypeCommon) event.getPacketType());
                data.packetTimestamps.add(now);
                data.packetTimestamps.removeIf(t -> now - t > 5000);
                if (!enabled) return;

                if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) {
                    WrapperPlayClientPlayerPosition position = new WrapperPlayClientPlayerPosition(event);
                    Vector3d pos = position.getPosition();
                    checkAllDetectors(player, data, now, pos.getX(), pos.getY(), pos.getZ());
                } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                    WrapperPlayClientPlayerPositionAndRotation posRot = new WrapperPlayClientPlayerPositionAndRotation(event);
                    Vector3d pos = posRot.getPosition();
                    checkAllDetectors(player, data, now, pos.getX(), pos.getY(), pos.getZ(), posRot.getYaw(), posRot.getPitch());
                } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                    WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
                    interactData.put(uuid, new InteractData(interact.getEntityId(), now));
                    detectors.get(CheatType.HIGH_CPS).check(player, data, now);
                }
            }
        });
    }

    private void checkAllDetectors(Player player, PlayerCheatData data, long now, double x, double y, double z, float... rotation) {
        detectors.get(CheatType.INVALID_MOVEMENT).check(player, data, now, x, y, z);
        detectors.get(CheatType.VERTICAL_TELEPORT).check(player, data, now, x, y, z);
        detectors.get(CheatType.BLINK).check(player, data, now);
        detectors.get(CheatType.FLY_HACK).check(player, data, now, x, y, z);
        detectors.get(CheatType.SPEED_HACK).check(player, data, now, x, y, z);
        if (rotation.length == 2) {
            detectors.get(CheatType.FAST_HEAD_ROTATION).check(player, data, now, rotation[0], rotation[1]);
        }
    }

    private void startInteractCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    InteractData data = interactData.remove(uuid);
                    if (data == null) continue;

                    PlayerCheatData cheat = cheatData.getOrDefault(uuid, new PlayerCheatData());
                    long now = System.currentTimeMillis();
                    Entity target = getEntityById(player, data.entityId);
                    if (target != null) {
                        double angleDeviation = getAngleDeviation(player, target);
                        double reachDistance = getReachDistance(player, target);
                        detectors.get(CheatType.KILLAURA).check(player, cheat, now, angleDeviation);
                        detectors.get(CheatType.REACH).check(player, cheat, now, reachDistance);
                    }
                }
            }
        }.runTaskTimer(context.getPlugin(), 0L, 1L);
    }

    private Entity getEntityById(Player player, int entityId) {
        return player.getWorld().getEntities().stream()
                .filter(e -> e.getEntityId() == entityId)
                .findFirst().orElse(null);
    }

    private double getAngleDeviation(Player player, Entity target) {
        Location eyeLoc = player.getEyeLocation();
        Vector playerDir = eyeLoc.getDirection();
        Vector toTarget = target.getLocation().toVector().subtract(eyeLoc.toVector()).normalize();
        double dot = playerDir.dot(toTarget);
        return Math.toDegrees(Math.acos(Math.min(Math.max(dot, -1.0), 1.0)));
    }

    private double getReachDistance(Player player, Entity target) {
        return player.getEyeLocation().distance(target.getLocation());
    }

    private void savePacketRecords(Player player, PlayerCheatData data) {
        Map<String, Object> packetRecord = new HashMap<>();
        packetRecord.put("total_packets", data.totalPackets);
        packetRecord.put("bps_avg", data.getAverageBps());
        packetRecord.put("timestamp", System.currentTimeMillis());
        config.set("packet_records." + player.getUniqueId(), packetRecord);
        saveConfig();
    }

    private void saveCheatRecord(Player player, CheatType type, String details) {
        Map<String, Object> record = new HashMap<>();
        record.put("type", type.name());
        record.put("details", details);
        record.put("timestamp", System.currentTimeMillis());
        String path = "cheat_records." + player.getUniqueId();
        List<Map<String, Object>> triggers = (List<Map<String, Object>>) config.getList(path, new ArrayList<>());
        triggers.add(record);
        if (triggers.size() > 100) triggers.remove(0);
        config.set(path, triggers);
        saveConfig();
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
        interactData.remove(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("darkpixel.admin")) {
            sender.sendMessage("§cYou don't have permission!");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /darkac <toggle|alert|detailedalert|status>");
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
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cOnly players can toggle alerts!");
                    return true;
                }
                toggleAlertPreference((Player) sender, false);
                sender.sendMessage("§eRegular alerts " + (isDetailedAlertEnabled((Player) sender) ? "disabled" : "enabled"));
                break;
            case "detailedalert":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cOnly players can toggle detailed alerts!");
                    return true;
                }
                toggleAlertPreference((Player) sender, true);
                sender.sendMessage("§eDetailed alerts " + (isDetailedAlertEnabled((Player) sender) ? "enabled" : "disabled"));
                break;
            case "status":
                sender.sendMessage("§eDarkAC is " + (enabled ? "enabled" : "disabled"));
                break;
            default:
                sender.sendMessage("§cUnknown subcommand!");
        }
        return true;
    }

    private void toggleAlertPreference(Player player, boolean detailed) {
        String uuid = player.getUniqueId().toString();
        ConfigurationSection prefsSection = config.getConfigurationSection("alert_prefs");
        Map<String, Object> prefs = prefsSection != null ? prefsSection.getValues(false) : new HashMap<>();
        prefs.put(uuid, detailed);
        config.set("alert_prefs", prefs);
        saveConfig();
    }

    private boolean isDetailedAlertEnabled(Player player) {
        String uuid = player.getUniqueId().toString();
        ConfigurationSection prefsSection = config.getConfigurationSection("alert_prefs");
        if (prefsSection == null) return false;
        Map<String, Object> prefs = prefsSection.getValues(false);
        Object value = prefs.getOrDefault(uuid, false);
        return value instanceof Boolean && (Boolean) value;
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
                    if (bps > maxBps) {
                        triggerAlert(player, CheatType.BLINK, "BPS: " + String.format("%.2f", bps));
                    }
                }
            }
        }.runTaskTimer(context.getPlugin(), 0L, 20L);
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                ConfigurationSection packetSection = config.getConfigurationSection("packet_records");
                Map<String, Object> packetRecords = packetSection != null ? packetSection.getValues(false) : new HashMap<>();
                int packetRemoved = packetRecords.size();
                packetRecords.entrySet().removeIf(e -> now - (long) ((Map<?, ?>) e.getValue()).get("timestamp") > 86400000);
                packetRemoved -= packetRecords.size();
                config.set("packet_records", packetRecords);

                ConfigurationSection cheatSection = config.getConfigurationSection("cheat_records");
                Map<String, Object> cheatRecords = cheatSection != null ? cheatSection.getValues(false) : new HashMap<>();
                int[] cheatRemoved = {0};
                cheatRecords.forEach((k, v) -> {
                    List<Map<String, Object>> triggers = (List<Map<String, Object>>) v;
                    int before = triggers.size();
                    triggers.removeIf(t -> now - (long) t.get("timestamp") > 604800000);
                    cheatRemoved[0] += before - triggers.size();
                });
                config.set("cheat_records", cheatRecords);

                saveConfig();
                LogUtil.info(String.format("Cleanup: Removed %d packet records, %d cheat records.", packetRemoved, cheatRemoved[0]));
            }
        }.runTaskTimer(context.getPlugin(), 0L, 1200L);
    }

    public void triggerAlert(Player player, CheatType type, String details) {
        UUID uuid = player.getUniqueId();
        Map<CheatType, Integer> counts = triggerCounts.computeIfAbsent(uuid, k -> new HashMap<>());
        int count = counts.getOrDefault(type, 0) + 1;
        counts.put(type, count);
        if (count >= 5) {
            String simpleAlert = String.format("[DarkAC] %s triggered %s: %s (x%d)", player.getName(), type.getName(), details, count);
            String detailedAlert = String.format("[DarkAC Detailed] %s triggered %s: %s (x%d) - BPS: %.2f, Pos: %.2f,%.2f,%.2f",
                    player.getName(), type.getName(), details, count, cheatData.get(uuid).getAverageBps(),
                    player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
            Bukkit.getOnlinePlayers().stream()
                    .filter(Player::isOp)
                    .forEach(op -> op.sendMessage("§e" + (isDetailedAlertEnabled(op) ? detailedAlert : simpleAlert)));
            LogUtil.warning(simpleAlert);
            saveCheatRecord(player, type, details);
            counts.put(type, 0);
        }
    }

    public void addReport(Player target, Player reporter) {
        reports.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>())
                .add(new Report(reporter.getUniqueId(), System.currentTimeMillis()));
    }

    static class InteractData {
        final int entityId;
        final long timestamp;

        InteractData(int entityId, long timestamp) {
            this.entityId = entityId;
            this.timestamp = timestamp;
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
}