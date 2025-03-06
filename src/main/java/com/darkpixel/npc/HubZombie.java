package com.darkpixel.npc;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.FixedMetadataValue;
public class HubZombie implements Listener {
    private final NpcHandler npcHandler;
    public HubZombie(NpcHandler npcHandler) {
        this.npcHandler = npcHandler;
    }
    public void configure(Zombie npc, Player creator, Location spawnLocation) {
        if (npc == null || !npc.isValid()) return;
        npc.setCustomName("§6大厅助手");
        npc.setCustomNameVisible(true);
        npc.setAI(false);
        npc.setCanPickupItems(false);
        npc.setSilent(true);
        npc.setInvulnerable(true);
        npc.setPersistent(true);
        npc.setRemoveWhenFarAway(false);
        npc.setTarget(null);
        npc.setMetadata("DarkPixelNPC", new FixedMetadataValue(npcHandler.configManager.getPlugin(), true));
        npc.setMetadata("DarkPixelNPCSpawn", new FixedMetadataValue(npcHandler.configManager.getPlugin(), spawnLocation));
        npc.setHealth(20.0);
        if (creator != null) {
            Location npcLocation = npc.getLocation();
            npcLocation.setYaw(creator.getLocation().getYaw());
            npcLocation.setPitch(creator.getLocation().getPitch());
            npc.teleport(npcLocation);
        }
    }
    public void onInteract(Player player, Zombie npc) {
        if (player == null || !player.isOnline() || npc == null || !npc.isValid()) return;
        npcHandler.dashboard.openMainDashboard(player);
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (player == null) return;
        for (Zombie npc : npcHandler.getNpcs()) {
            if (npc != null && npc.isValid() && npc.getWorld().equals(player.getWorld())) {
                player.sendMessage("§e检测到一个大厅 NPC 在附近: " + npc.getEntityId());
            }
        }
    }
}