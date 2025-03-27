package com.darkpixel.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReportCommand implements CommandExecutor {
    private final AntiCheatHandler handler;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_TIME = 30000L;

    public ReportCommand(AntiCheatHandler handler) {
        this.handler = handler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c仅玩家可使用此命令！");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§c用法: /report <玩家名>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§c玩家 " + args[0] + " 不在线！");
            return true;
        }
        if (target.equals(player)) {
            sender.sendMessage("§c不能举报自己！");
            return true;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(uuid) && (now - cooldowns.get(uuid)) < COOLDOWN_TIME) {
            long remaining = (COOLDOWN_TIME - (now - cooldowns.get(uuid))) / 1000;
            player.sendMessage("§c请等待 " + remaining + " 秒后再举报！");
            return true;
        }
        cooldowns.put(uuid, now);
        handler.addReport(target, player);
        player.sendMessage("§a已举报 " + target.getName() + "！");
        return true;
    }
}