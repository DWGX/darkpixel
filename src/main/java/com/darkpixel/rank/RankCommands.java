package com.darkpixel.rank;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class RankCommands implements CommandExecutor {
    private final RankManager rankManager;

    public RankCommands(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("darkpixel.rank")) {
            sender.sendMessage("§c没权限，哥们！");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用错了！格式: /rank <玩家名> <rank> <分数>");
            return true;
        }

        String playerName = args[0];
        String rank = args[1];
        int score;
        try {
            score = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c分数得是数字啊！");
            return true;
        }

        UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        // 设置Rank，默认粒子和消息
        rankManager.setRankByUUID(uuid, rank, score, Particle.FIREWORK, "欢迎 {player} 加入服务器！");
        sender.sendMessage("§a搞定！" + playerName + " 的 Rank 现在是 " + rank + "，分数 " + score);
        return true;
    }
}