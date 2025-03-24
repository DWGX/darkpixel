package com.darkpixel.npc;

import com.darkpixel.Global;
import com.darkpixel.gui.DashboardHandler;
import com.darkpixel.gui.ServerSwitchChest;
import com.darkpixel.gui.ServerRadioChest;
import com.darkpixel.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NpcHandler implements Listener, CommandExecutor, TabCompleter {
    final ConfigManager configManager;
    final DashboardHandler dashboard;
    final ServerSwitchChest serverSwitchChest;
    final ServerRadioChest serverRadioChest;
    private final List<Zombie> npcs = new ArrayList<>();
    private final Map<String, Zombie> npcById = new HashMap<>();

    public NpcHandler(ConfigManager configManager, DashboardHandler dashboard, ServerSwitchChest serverSwitchChest, ServerRadioChest serverRadioChest) {
        this.configManager = configManager;
        this.dashboard = dashboard;
        this.serverSwitchChest = serverSwitchChest;
        this.serverRadioChest = serverRadioChest;
        loadNpcsPersistently();
        startPositionLockAndReloadTask();
    }

    private void loadNpcsPersistently() {
        // 在主线程中加载 NPC，避免异步操作
        Bukkit.getScheduler().runTask(configManager.getPlugin(), () -> {
            reloadMissingNpcs();
            configManager.getPlugin().getLogger().info("[NpcHandler] NPC 加载完成");
        });
    }

    private void reloadMissingNpcs() {
        List<String> npcLocations = configManager.getNpcLocations();
        if (npcLocations == null || npcLocations.isEmpty()) return;

        List<String> loadedInfo = new ArrayList<>();
        for (String locStr : npcLocations) {
            String[] parts = locStr.split(",");
            if (parts.length != 7) continue;
            Location loc = new Location(Bukkit.getWorld(parts[0]),
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
            String customId = parts[6];
            if (loc.getWorld() == null) continue;

            if (!loc.getChunk().isLoaded()) {
                loc.getChunk().load();
            }

            Zombie existingNpc = findExistingNpcAt(loc, customId);
            if (existingNpc != null && npcs.contains(existingNpc)) continue;

            clearNonNpcZombies(loc);
            if (existingNpc != null) {
                configureNpc(existingNpc, loc, customId);
                npcs.add(existingNpc);
                if (customId != null && !customId.startsWith("auto_")) npcById.put(customId, existingNpc);
            } else {
                spawnNpc(loc, customId);
            }
            loadedInfo.add("Loaded NPC at " + loc.toString() + " with ID: " + customId);
        }
        cleanOldNpcs();
        if (!loadedInfo.isEmpty()) {
            Bukkit.getLogger().info("[NpcHandler] " + String.join("; ", loadedInfo));
        }
    }

    private void clearNonNpcZombies(Location loc) {
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
            if (entity instanceof Zombie zombie && !zombie.hasMetadata("DarkPixelNPC")) {
                zombie.remove();
                Bukkit.getLogger().info("[NpcHandler] Removed non-NPC zombie at " + loc.toString());
            }
        }
    }

    private Zombie findExistingNpcAt(Location loc, String customId) {
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1.0, 1.0, 1.0)) {
            if (entity instanceof Zombie zombie && zombie.hasMetadata("DarkPixelNPC")) {
                Location npcLoc = zombie.getLocation();
                if (Math.abs(npcLoc.getX() - loc.getX()) < 0.5 &&
                        Math.abs(npcLoc.getY() - loc.getY()) < 0.5 &&
                        Math.abs(npcLoc.getZ() - loc.getZ()) < 0.5) {
                    String npcId = npcById.entrySet().stream()
                            .filter(entry -> entry.getValue().equals(zombie))
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse("auto_" + zombie.getEntityId());
                    if (npcId.equals(customId)) {
                        return zombie;
                    }
                }
            }
        }
        return null;
    }

    private void cleanOldNpcs() {
        List<Zombie> toRemove = new ArrayList<>();
        for (Zombie npc : npcs) {
            if (!npc.isValid() || !npc.hasMetadata("DarkPixelNPC")) {
                npc.remove();
                toRemove.add(npc);
                Bukkit.getLogger().info("[NpcHandler] Removed invalid NPC: " + npc.getUniqueId());
            }
        }
        npcs.removeAll(toRemove);
        npcById.entrySet().removeIf(entry -> toRemove.contains(entry.getValue()));
        saveNpcs();
    }

    private void spawnNpc(Location location, String customId) {
        if (customId != null && npcById.containsKey(customId)) {
            Zombie oldNpc = npcById.get(customId);
            oldNpc.remove();
            npcs.remove(oldNpc);
            npcById.remove(customId);
            Bukkit.getLogger().info("[NpcHandler] Removed conflicting NPC with ID " + customId);
        }

        clearNonNpcZombies(location);
        Zombie oldNpcAtLocation = findExistingNpcAt(location, customId);
        if (oldNpcAtLocation != null) {
            oldNpcAtLocation.remove();
            npcs.remove(oldNpcAtLocation);
            npcById.entrySet().removeIf(entry -> entry.getValue().equals(oldNpcAtLocation));
            Bukkit.getLogger().info("[NpcHandler] Removed old NPC at " + location.toString());
        }

        Zombie npc = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        configureNpc(npc, location, customId);
        npcs.add(npc);
        if (customId != null && !customId.startsWith("auto_")) npcById.put(customId, npc);
        saveNpcs();
    }

    private void configureNpc(Zombie npc, Location location, String customId) {
        npc.setBaby(false);
        npc.setCustomName("§a大厅NPC");
        npc.setCustomNameVisible(true);
        npc.setSilent(true);
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setMetadata("DarkPixelNPC", new FixedMetadataValue(configManager.getPlugin(), true));
        npc.setMetadata("DarkPixelNPCSpawn", new FixedMetadataValue(configManager.getPlugin(), location.clone()));
        if ("switchChest".equals(customId)) {
            npc.setCustomName("§a切换服务器NPC");
            npc.setMetadata("switchChest", new FixedMetadataValue(configManager.getPlugin(), true));
        } else if ("radioChest".equals(customId)) {
            npc.setCustomName("§a音乐控制NPC");
            npc.setMetadata("radioChest", new FixedMetadataValue(configManager.getPlugin(), true));
        }
    }

    private void removeAllNpcs(Player player) {
        if (npcs.isEmpty()) {
            player.sendMessage("§c当前没有大厅 NPC");
            return;
        }
        for (Zombie npc : new ArrayList<>(npcs)) npc.remove();
        npcs.clear();
        npcById.clear();
        saveNpcs();
        player.sendMessage("§a已清除所有大厅 NPC");
    }

    private void removeNpcById(String id, Player player) {
        Zombie target = npcById.get(id);
        if (target == null) {
            try {
                int entityId = Integer.parseInt(id);
                target = npcs.stream().filter(npc -> npc.getEntityId() == entityId).findFirst().orElse(null);
            } catch (NumberFormatException ignored) {}
        }
        if (target != null) {
            target.remove();
            npcs.remove(target);
            Zombie finalTarget = target;
            npcById.entrySet().removeIf(entry -> entry.getValue().equals(finalTarget));
            saveNpcs();
            player.sendMessage("§a已移除NPC，ID: " + id);
        } else {
            player.sendMessage("§c未找到ID为 " + id + " 的NPC");
        }
    }

    private void startPositionLockAndReloadTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                reloadMissingNpcs();
                for (Zombie npc : new ArrayList<>(npcs)) {
                    if (!npc.isValid() || !npc.hasMetadata("DarkPixelNPC")) {
                        npcs.remove(npc);
                        continue;
                    }
                    if (npc.hasMetadata("DarkPixelNPCSpawn")) {
                        Location spawnLoc = (Location) npc.getMetadata("DarkPixelNPCSpawn").get(0).value();
                        if (!npc.getLocation().equals(spawnLoc)) {
                            npc.teleport(spawnLoc);
                            Bukkit.getLogger().info("[NpcHandler] Teleported NPC to " + spawnLoc.toString());
                        }
                    }
                }
            }
        }.runTaskTimer(configManager.getPlugin(), 0L, 600L);
    }

    private void saveNpcs() {
        List<String> npcLocations = new ArrayList<>();
        for (Zombie npc : npcs) {
            if (npc.isValid() && npc.hasMetadata("DarkPixelNPC")) {
                Location loc = npc.getLocation();
                String customId = npcById.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(npc))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("auto_" + npc.getEntityId());
                npcLocations.add(String.format("%s,%f,%f,%f,%f,%f,%s",
                        loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), customId));
            }
        }
        configManager.saveNpcLocations(npcLocations);
        //Bukkit.getLogger().info("[NpcHandler] Saved " + npcLocations.size() + " NPCs to config.");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Zombie zombie && zombie.hasMetadata("DarkPixelNPC") && !npcs.contains(zombie)) {
                npcs.add(zombie);
                Location spawnLoc = (Location) zombie.getMetadata("DarkPixelNPCSpawn").get(0).value();
                if (!zombie.getLocation().equals(spawnLoc)) zombie.teleport(spawnLoc);
                Bukkit.getLogger().info("[NpcHandler] Re-added NPC from chunk load at " + spawnLoc.toString());
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Zombie zombie && zombie.hasMetadata("DarkPixelNPC")) {
            e.setCancelled(true);
            zombie.setHealth(20.0);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent e) {
        if (e.getEntity() instanceof Zombie && e.getEntity().hasMetadata("DarkPixelNPC")) {
            e.setTarget(null);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Zombie zombie && zombie.hasMetadata("DarkPixelNPC")) {
            Player player = e.getPlayer();
            if (zombie.hasMetadata("switchChest")) {
                serverSwitchChest.onCommand(player, null, "getswitchchest", new String[0]);
            } else if (zombie.hasMetadata("radioChest")) {
                serverRadioChest.onCommand(player, null, "getradio", new String[0]);
            } else {
                if (dashboard != null) {
                    dashboard.openMainDashboard(player);
                }
            }
            e.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c仅玩家可用");
            return true;
        }
        if (!player.hasPermission("darkpixel.npc.manage")) {
            player.sendMessage("§c只有OP才能用这个命令哦，找管理员喵~！");
            return true;
        }
        if (args.length == 0) {
            spawnNpc(player.getLocation(), null);
            player.sendMessage("§a成功生成大厅NPC");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "clear": removeAllNpcs(player); break;
            case "remove":
                if (args.length == 2) removeNpcById(args[1], player);
                else player.sendMessage("§c用法: /npc remove <id>");
                break;
            case "id":
                if (args.length == 2) {
                    spawnNpc(player.getLocation(), args[1]);
                    player.sendMessage("§a成功生成大厅NPC，ID: " + args[1]);
                } else player.sendMessage("§c用法: /npc id <custom_id>");
                break;
            case "switch":
                spawnNpc(player.getLocation(), "switchChest");
                player.sendMessage("§a成功生成切换服务器NPC");
                break;
            case "radio":
                spawnNpc(player.getLocation(), "radioChest");
                player.sendMessage("§a成功生成音乐控制NPC");
                break;
            default: player.sendMessage("§c用法: /npc [clear|remove <id>|id <custom_id>|switch|radio]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("clear");
            suggestions.add("remove");
            suggestions.add("id");
            suggestions.add("switch");
            suggestions.add("radio");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            suggestions.addAll(npcById.keySet());
        }
        return suggestions;
    }

    public List<Zombie> getNpcs() {
        return new ArrayList<>(npcs);
    }
}