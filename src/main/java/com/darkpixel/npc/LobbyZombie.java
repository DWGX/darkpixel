package com.darkpixel.npc;

import com.darkpixel.gui.DashboardHandler;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class LobbyZombie implements Listener {
    private final DashboardHandler dashboard;

    public LobbyZombie(DashboardHandler dashboard) {
        this.dashboard = dashboard;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Zombie zombie && !zombie.hasMetadata("switchChest") && !zombie.hasMetadata("radioChest")) {
            Player player = e.getPlayer();
            if (dashboard != null) {
                dashboard.openMainDashboard(player);
            }
            e.setCancelled(true);
        }
    }
}