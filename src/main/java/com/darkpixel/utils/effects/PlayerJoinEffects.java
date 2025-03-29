package com.darkpixel.utils.effects;

import com.darkpixel.utils.PlayerData;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinEffects implements Listener {
    private final PlayerData playerData;

    public PlayerJoinEffects(PlayerData playerData) {
        this.playerData = playerData;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData.PlayerInfo info = playerData.getPlayerInfo(player.getName());
        if (info.effects_enabled) {
            player.getWorld().spawnParticle(info.particle, player.getLocation(), 100);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.getServer().broadcastMessage("§e" + player.getName() + " §a加入了服务器 (§b" + String.join(", ", info.groups) + "§a)");
        }
    }
}