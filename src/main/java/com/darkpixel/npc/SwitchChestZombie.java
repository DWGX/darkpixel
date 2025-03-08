package com.darkpixel.npc;

import com.darkpixel.gui.ServerSwitchChest;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class SwitchChestZombie implements Listener {
    private final ServerSwitchChest serverSwitchChest;

    public SwitchChestZombie(ServerSwitchChest serverSwitchChest) {
        this.serverSwitchChest = serverSwitchChest;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Zombie zombie && zombie.hasMetadata("switchChest")) {
            Player player = e.getPlayer();
            serverSwitchChest.onCommand(player, null, "getswitchchest", new String[0]);
            e.setCancelled(true);
        }
    }
}