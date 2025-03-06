package com.darkpixel.npc;
import com.darkpixel.gui.DashboardHandler;
import com.darkpixel.manager.ConfigManager;
import com.darkpixel.utils.LogUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class NpcHandler implements Listener, CommandExecutor {
    final ConfigManager configManager;
    final DashboardHandler dashboard;
    private final List<Zombie> npcs = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Zombie> npcById = new HashMap<>();
    private final HubZombie hubZombie;
    public NpcHandler(ConfigManager configManager, DashboardHandler dashboard) {
        this.configManager = configManager;
        this.dashboard = dashboard;
        this.hubZombie = new HubZombie(this);
        loadNpcsPersistently();
        startPositionLockTask();
    }
    public void reloadConfig() {
        loadNpcsPersistently();
        LogUtil.info("NPC 配置已重新加载");
    }
    public List<Zombie> getNpcs() {
        return Collections.unmodifiableList(npcs);
    }
    private void loadNpcsPersistently() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<String> npcLocations = configManager.getNpcLocations();
                if (npcLocations == null || npcLocations.isEmpty()) return;
                for (String locStr : npcLocations) {
                    String[] parts = locStr.split(",");
                    if (parts.length != 7) continue;
                    try {
                        Location loc = new Location(Bukkit.getWorld(parts[0]),
                                Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                                Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
                        String customId = parts[6];
                        if (loc.getWorld() != null) {
                            clearNonNpcZombies(loc);
                            Zombie existingNpc = findExistingNpcAt(loc);
                            if (existingNpc != null && !npcs.contains(existingNpc)) {
                                hubZombie.configure(existingNpc, null, loc);
                                npcs.add(existingNpc);
                                if (customId != null && !customId.startsWith("auto_")) npcById.put(customId, existingNpc);
                            } else {
                                spawnNpc(loc, null, customId);
                            }
                        }
                    } catch (NumberFormatException e) {
                        LogUtil.warning("Invalid NPC location format: " + locStr);
                    }
                }
                cleanOldNpcs();
            }
        }.runTask(configManager.getPlugin());
    }
    private void clearNonNpcZombies(Location loc) {
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1.0, 1.0, 1.0)) {
            if (entity instanceof Zombie zombie && !zombie.hasMetadata("DarkPixelNPC")) zombie.remove();
        }
    }
    private Zombie findExistingNpcAt(Location loc) {
        return loc.getWorld().getNearbyEntities(loc, 1.0, 1.0, 1.0).stream()
                .filter(e -> e instanceof Zombie && e.hasMetadata("DarkPixelNPC"))
                .map(e -> (Zombie) e)
                .findFirst()
                .orElse(null);
    }
    private void cleanOldNpcs() {
        List<Zombie> toRemove = new ArrayList<>();
        for (Zombie npc : npcs) {
            if (!npc.isValid() || !npc.hasMetadata("DarkPixelNPC")) {
                npc.remove();
                toRemove.add(npc);
            }
        }
        npcs.removeAll(toRemove);
        npcById.entrySet().removeIf(entry -> toRemove.contains(entry.getValue()));
        saveNpcs();
    }
    private void spawnNpc(Location location, Player creator, String customId) {
        if (customId != null && npcById.containsKey(customId)) {
            if (creator != null) creator.sendMessage("§cID " + customId + " 已存在，请用其他ID");
            return;
        }
        clearNonNpcZombies(location);
        Zombie oldNpc = findExistingNpcAt(location);
        if (oldNpc != null) {
            oldNpc.remove();
            npcs.remove(oldNpc);
            npcById.entrySet().removeIf(entry -> entry.getValue().equals(oldNpc));
        }
        Zombie npc = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        hubZombie.configure(npc, creator, location);
        npcs.add(npc);
        if (customId != null && !customId.startsWith("auto_")) npcById.put(customId, npc);
        saveNpcs();
        if (creator != null) creator.sendMessage("§a成功生成大厅NPC，ID: " + (customId != null ? customId : npc.getEntityId()));
    }
    private void removeAllNpcs(Player player) {
        if (npcs.isEmpty()) {
            player.sendMessage("§c当前没有大厅 NPC");
            return;
        }
        for (Zombie npc : new ArrayList<>(npcs)) {
            npc.remove();
            npcs.remove(npc);
        }
        npcById.clear();
        saveNpcs();
        player.sendMessage("§a已移除所有大厅 NPC");
    }
    private void saveNpcs() {
        List<String> locations = new ArrayList<>();
        for (Zombie npc : npcs) {
            Location loc = npc.getLocation();
            String customId = npcById.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(npc))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("auto_" + npc.getEntityId());
            locations.add(String.format("%s,%f,%f,%f,%f,%f,%s",
                    loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch(), customId));
        }
        configManager.saveNpcLocations(locations);
    }
    private void startPositionLockTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Zombie npc : new ArrayList<>(npcs)) {
                    if (!npc.isValid()) {
                        npcs.remove(npc);
                        npcById.entrySet().removeIf(entry -> entry.getValue().equals(npc));
                        continue;
                    }
                    Location intended = npc.getMetadata("intendedLocation")
                            .get(0).asString()
                            .split(",")
                            .length == 6 ? deserializeLocation(npc.getMetadata("intendedLocation").get(0).asString()) : npc.getLocation();
                    if (!npc.getLocation().equals(intended)) {
                        npc.teleport(intended);
                    }
                }
            }
        }.runTaskTimer(configManager.getPlugin(), 0L, 20L);
    }
    private Location deserializeLocation(String locStr) {
        String[] parts = locStr.split(",");
        return new Location(Bukkit.getWorld(parts[0]),
                Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("darkpixel.admin")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§c用法: /npc <spawn|remove|list>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "spawn":
                String customId = args.length > 1 ? args[1] : null;
                spawnNpc(player.getLocation(), player, customId);
                break;
            case "remove":
                if (args.length > 1) {
                    Zombie npc = npcById.remove(args[1]);
                    if (npc != null) {
                        npc.remove();
                        npcs.remove(npc);
                        saveNpcs();
                        player.sendMessage("§a已移除 NPC ID: " + args[1]);
                    } else {
                        player.sendMessage("§c未找到 ID 为 " + args[1] + " 的 NPC");
                    }
                } else {
                    removeAllNpcs(player);
                }
                break;
            case "list":
                if (npcs.isEmpty()) {
                    player.sendMessage("§c当前没有大厅 NPC");
                } else {
                    player.sendMessage("§a大厅 NPC 列表:");
                    npcById.forEach((id, npc) -> player.sendMessage("§7- ID: " + id + " @ " + formatLocation(npc.getLocation())));
                    npcs.stream().filter(npc -> npcById.values().stream().noneMatch(n -> n.equals(npc)))
                            .forEach(npc -> player.sendMessage("§7- AutoID: " + npc.getEntityId() + " @ " + formatLocation(npc.getLocation())));
                }
                break;
            default:
                player.sendMessage("§c未知子命令: " + args[0]);
        }
        return true;
    }
    private String formatLocation(Location loc) {
        return String.format("%s (%.1f, %.1f, %.1f)", loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity instanceof Zombie zombie && zombie.hasMetadata("DarkPixelNPC")) {
            event.setCancelled(true);
            hubZombie.onInteract(event.getPlayer(), zombie);
        }
    }
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Zombie zombie && zombie.hasMetadata("DarkPixelNPC")) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Zombie zombie && zombie.hasMetadata("DarkPixelNPC")) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Zombie zombie && zombie.hasMetadata("DarkPixelNPC") && !npcs.contains(zombie)) {
                Location intended = deserializeLocation(zombie.getMetadata("intendedLocation").get(0).asString());
                zombie.teleport(intended);
                npcs.add(zombie);
                String customId = zombie.getMetadata("customId").get(0).asString();
                if (!customId.startsWith("auto_")) npcById.put(customId, zombie);
            }
        }
    }
}