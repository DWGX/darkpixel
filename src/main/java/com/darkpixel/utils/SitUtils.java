package com.darkpixel.utils;

import com.darkpixel.Global;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.EventPriority;

public class SitUtils implements Listener {
    private final Global plugin;

    public SitUtils(Global plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getConfigManager().isSittingEnabled()) return;
        if (!plugin.getConfigManager().isSittingOnPlayersAllowed()) return;
        if (plugin.getConfigManager().getSittingBlockedWorlds().contains(event.getPlayer().getWorld().getName())) return;
        if (event.getPlayer().isSneaking()) return;
        if (!(event.getRightClicked() instanceof Player other)) return;
        other.addPassenger(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!plugin.getConfigManager().isSittingEnabled()) return;
        if (!plugin.getConfigManager().isSittingOnBlocksAllowed()) return;
        if (plugin.getConfigManager().getSittingBlockedWorlds().contains(event.getClickedBlock().getWorld().getName())) return;
        if (event.getPlayer().isSneaking()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        for (String block : plugin.getConfigManager().getValidSittingBlocks()) {
            if (event.getClickedBlock().getType().name().toLowerCase().contains(block.toLowerCase())) {
                sitDown(event.getPlayer(), event.getClickedBlock(), true);
                break;
            }
        }
    }

    @EventHandler
    public void onVehicleExit(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getDismounted() instanceof ArmorStand stand) {
            Location standLocation = stand.getLocation();
            Location newLocation = standLocation.clone().add(0, 1.0, 0);
            newLocation.setYaw(player.getLocation().getYaw());
            newLocation.setPitch(player.getLocation().getPitch());
            player.teleportAsync(newLocation);
            stand.remove();
        }
    }

    public void sitDown(Player player, Block block, boolean adjustHeight) {
        Location seatLocation = block.getLocation().add(0.5, adjustHeight ? 0.3 : 0.5, 0.5);
        ArmorStand seat = (ArmorStand) player.getWorld().spawnEntity(seatLocation, EntityType.ARMOR_STAND);
        seat.setGravity(false);
        seat.setVisible(false);
        seat.setMarker(true);
        seat.setSmall(true);
        seat.addPassenger(player);
    }
}