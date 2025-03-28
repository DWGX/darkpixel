package com.darkpixel.rank;

import com.darkpixel.Global;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RankCommands implements CommandExecutor {
    private final Global context;

    public RankCommands(Global context) {
        this.context = context;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("rank")) return false;

        if (!sender.hasPermission("darkpixel.admin")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§c用法: /rank ban <玩家> <时间(分钟，perm为永久)> [原因] 或 /rank unban <玩家>");
            return true;
        }

        if (args[0].equalsIgnoreCase("ban")) {
            if (args.length < 3) {
                sender.sendMessage("§c用法: /rank ban <玩家> <时间(分钟，perm为永久)> [原因]");
                return true;
            }
            String playerName = args[1];
            String timeStr = args[2];
            long banUntil;
            if (timeStr.equalsIgnoreCase("perm")) {
                banUntil = -1;
            } else {
                try {
                    long banTime = Long.parseLong(timeStr);
                    if (banTime <= 0) {
                        sender.sendMessage("§c时间必须是正数或 'perm'！");
                        return true;
                    }
                    banUntil = System.currentTimeMillis() + banTime * 60000;
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c时间必须是数字或 'perm'！");
                    return true;
                }
            }
            String reason = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "未指定原因";

            // 调用 BanManager 执行封禁并同步到数据库
            context.getBanManager().banPlayer(playerName, banUntil, reason);
            sender.sendMessage("§a已封禁 " + playerName + "，时长: " + (banUntil == -1 ? "永久" : timeStr + "分钟") + "，原因: " + reason);
            return true;
        }

        if (args[0].equalsIgnoreCase("unban")) {
            if (args.length != 2) {
                sender.sendMessage("§c用法: /rank unban <玩家>");
                return true;
            }
            String playerName = args[1];

            // 调用 BanManager 执行解封并同步到数据库
            context.getBanManager().unbanPlayer(playerName);
            sender.sendMessage("§a已解封 " + playerName);
            return true;
        }

        sender.sendMessage("§c未知子命令！用法: /rank ban 或 /rank unban");
        return true;
    }
}