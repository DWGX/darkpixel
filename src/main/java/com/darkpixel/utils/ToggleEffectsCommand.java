package com.darkpixel.utils;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ToggleEffectsCommand implements CommandExecutor {
    private final PlayerData playerData;

    public ToggleEffectsCommand(PlayerData playerData) {
        this.playerData = playerData;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c仅玩家可用");
            return true;
        }
        PlayerData.PlayerInfo info = playerData.getPlayerInfo(player.getName());
        info.effectsEnabled = !info.effectsEnabled;
        playerData.saveData();
        player.sendMessage("§a进服特效已" + (info.effectsEnabled ? "开启" : "关闭"));
        return true;
    }
}