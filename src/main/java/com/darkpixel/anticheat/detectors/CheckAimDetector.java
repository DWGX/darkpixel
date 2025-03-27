package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.CheckPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class CheckAimDetector extends AbstractCheckDetector {
    public CheckAimDetector(YamlConfiguration config, AntiCheatHandler handler, Map<UUID, CheckPlayer> checkPlayers) {
        super(config, handler, checkPlayers);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType().equals(PacketType.Play.Client.INTERACT_ENTITY)) {
            WrapperPlayClientInteractEntity interactEntity = new WrapperPlayClientInteractEntity(event);
            CheckPlayer checkPlayer = checkPlayers.get(((org.bukkit.entity.Player) event.getPlayer()).getUniqueId());

            if (checkPlayer == null) return;

            Bukkit.getScheduler().runTask(handler.getContext().getPlugin(), () -> {
                Entity found = null;

                for (Entity entity : checkPlayer.getPlayer().getWorld().getEntities()) {
                    if (entity.getEntityId() == interactEntity.getEntityId()) {
                        found = entity;
                        break;
                    }
                }

                if (found != null) {
                    Vector playerEyeLoc = checkPlayer.getPlayer().getEyeLocation().toVector();
                    Vector targetLoc = found.getLocation().toVector();
                    Vector direction = targetLoc.subtract(playerEyeLoc).normalize();
                    float yaw = (float) Math.toDegrees(Math.atan2(direction.getZ(), direction.getX())) - 90;
                    if (yaw < 0) yaw += 360;

                    if (Math.abs(yaw - checkPlayer.getYaw()) < 0.01) {
                        handler.triggerAlert(checkPlayer.getPlayer(), AntiCheatHandler.CheatType.AIM,
                                "Suspicious aim detected");
                    }
                }
            });
        }
    }
}