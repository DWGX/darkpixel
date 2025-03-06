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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private boolean enabled = false;
    private final Map<UUID, PlayerCheatData> cheatData = new ConcurrentHashMap<>();
    private final Map<UUID, List<Report>> reports = new ConcurrentHashMap<>();
    private final Map<CheatType, Detector> detectors = new EnumMap<>(CheatType.class);
    private final double maxBps;
    private final Map<UUID, Map<String, Object>> packetCache = new ConcurrentHashMap<>();
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
    }
    private void loadConfig() {
        config = FileUtil.loadOrCreate(configFile, context.getPlugin(), "darkac.yml");
        config.addDefault("enabled", false);
        config.addDefault("report_threshold", 2);
        config.addDefault("report_window_ms", 300000L);
        config.addDefault("detectors.blink.max_bps", 50.0);
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
                    double x = pos.getX();
                    double y = pos.getY();
                    double z = pos.getZ();
                    checkAllDetectors(player, data, now, x, y, z);
                } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                    WrapperPlayClientPlayerPositionAndRotation posRot = new WrapperPlayClientPlayerPositionAndRotation(event);
                    Vector3d pos = posRot.getPosition();
                    double x = pos.getX();
                    double y = pos.getY();
                    double z = pos.getZ();
                    float yaw = posRot.getYaw();
                    float pitch = posRot.getPitch();
                    checkAllDetectors(player, data, now, x, y, z, yaw, pitch);
                } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                    WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
                    detectors.get(CheatType.HIGH_CPS).check(player, data, now);
                    detectors.get(CheatType.KILLAURA).check(player, data, now, getAngleDeviation(player, interact));
                    detectors.get(CheatType.REACH).check(player, data, now, getReachDistance(player, interact));
                }
                if (data.totalPackets % 100 == 0) {
                    savePacketRecords(player, data);
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
    private double getAngleDeviation(Player player, WrapperPlayClientInteractEntity interact) {
        int entityId = interact.getEntityId();
        Entity target = player.getWorld().getEntities().stream()
                .filter(e -> e.getEntityId() == entityId)
                .findFirst().orElse(null);
        if (target == null) return 0.0;
        Location eyeLoc = player.getEyeLocation();
        Vector playerDir = eyeLoc.getDirection();
        Vector toTarget = target.getLocation().toVector().subtract(eyeLoc.toVector()).normalize();
        return Math.toDegrees(Math.acos(playerDir.dot(toTarget)));
    }
    private double getReachDistance(Player player, WrapperPlayClientInteractEntity interact) {
        int entityId = interact.getEntityId();
        Entity target = player.getWorld().getEntities().stream()
                .filter(e -> e.getEntityId() == entityId)
                .findFirst().orElse(null);
        return target != null ? player.getEyeLocation().distance(target.getLocation()) : 0.0;
    }
    private void savePacketRecords(Player player, PlayerCheatData data) {
        Map<String, Object> packetRecord = new HashMap<>();
        packetRecord.put("total_packets", data.totalPackets);
        packetRecord.put("bps_avg", data.getAverageBps());
        packetCache.put(player.getUniqueId(), packetRecord);
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
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("darkpixel.admin")) {
            sender.sendMessage("§cYou don't have permission!");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /darkac <on|off|toggle|alert|status>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "on":
                enabled = true;
                config.set("enabled", true);
                saveConfig();
                sender.sendMessage("§aDarkAC enabled!");
                break;
            case "off":
                enabled = false;
                config.set("enabled", false);
                saveConfig();
                sender.sendMessage("§cDarkAC disabled!");
                break;
            case "toggle":
                enabled = !enabled;
                config.set("enabled", enabled);
                saveConfig();
                sender.sendMessage("§eDarkAC " + (enabled ? "enabled" : "disabled"));
                break;
            case "alert":
                for (Player p : Bukkit.getOnlinePlayers()) {
                    PlayerCheatData data = cheatData.getOrDefault(p.getUniqueId(), new PlayerCheatData());
                    sender.sendMessage("§e" + p.getName() + ": BPS Avg=" + String.format("%.2f", data.getAverageBps()));
                }
                break;
            case "status":
                sender.sendMessage("§eDarkAC is " + (enabled ? "enabled" : "disabled"));
                break;
            default:
                sender.sendMessage("§cUnknown subcommand!");
        }
        return true;
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
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerCheatData data = cheatData.getOrDefault(player.getUniqueId(), new PlayerCheatData());
                    double bps = data.getAverageBps();
                    if (bps > maxBps && enabled) {
                        triggerAlert(player, CheatType.BLINK, "BPS: " + String.format("%.2f", bps));
                    }
                }
            }
        }.runTaskTimer(context.getPlugin(), 0L, 20L);
    }
    public void triggerAlert(Player player, CheatType type, String details) {
        String alert = String.format("[DarkAC] %s triggered %s: %s", player.getName(), type.getName(), details);
        if (enabled) {
            Bukkit.getOnlinePlayers().stream()
                    .filter(Player::isOp)
                    .forEach(op -> op.sendMessage(alert));
            LogUtil.warning(alert);
        }
    }
    public void addReport(Player target, Player reporter) {
        List<Report> reportList = reports.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>());
        reportList.add(new Report(reporter.getUniqueId(), System.currentTimeMillis()));
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