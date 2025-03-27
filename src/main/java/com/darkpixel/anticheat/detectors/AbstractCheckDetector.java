package com.darkpixel.anticheat.detectors;

import com.darkpixel.anticheat.AntiCheatHandler;
import com.darkpixel.anticheat.CheckPlayer;
import com.darkpixel.anticheat.Detector;
import com.darkpixel.anticheat.PlayerCheatData;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractCheckDetector implements Detector {
    protected final AntiCheatHandler handler;
    protected final Map<UUID, CheckPlayer> checkPlayers;

    public AbstractCheckDetector(YamlConfiguration config, AntiCheatHandler handler, Map<UUID, CheckPlayer> checkPlayers) {
        this.handler = handler;
        this.checkPlayers = checkPlayers;
    }

    @Override
    public void check(Player player, PlayerCheatData data, long timestamp, double... args) {
    }

    public abstract void onPacketReceive(PacketReceiveEvent event);
}