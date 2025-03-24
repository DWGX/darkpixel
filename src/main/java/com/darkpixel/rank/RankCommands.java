package com.darkpixel.rank;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class RankCommands implements CommandExecutor {
    private final RankManager rankManager;

    public RankCommands(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0].toLowerCase()) {
            case "set":
                if (args.length < 4) return false;
                UUID uuid;
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    uuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                } else {
                    uuid = target.getUniqueId();
                }
                String rank = args[2];
                int score = Integer.parseInt(args[3]);
                rankManager.setRankByUUID(uuid, rank, score);
                sender.sendMessage("§a已设置 " + args[1] + " 的等级为 " + rank + "，分数: " + score);
                return true;
            case "group":
                if (args.length < 3) return false;
                Player groupTarget = Bukkit.getPlayer(args[1]);
                if (groupTarget == null) return false;
                rankManager.setGroup(groupTarget, args[2]);
                sender.sendMessage("§a已将 " + args[1] + " 设置为 " + args[2] + " 组");
                return true;
            default:
                return false;
        }
    }
}