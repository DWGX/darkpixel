package com.darkpixel.npc;

import com.darkpixel.gui.ServerRadioChest;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class RadioChestZombie implements Listener {
    private final ServerRadioChest serverRadioChest;

    public RadioChestZombie(ServerRadioChest serverRadioChest) {
        this.serverRadioChest = serverRadioChest;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Zombie zombie && zombie.hasMetadata("radioChest")) {
            Player player = e.getPlayer();
            serverRadioChest.onCommand(player, null, "getradio", new String[0]);
            e.setCancelled(true);
        }
    }
}