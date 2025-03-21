package com.darkpixel.rank;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RankCommands implements CommandExecutor, TabCompleter {
    private final RankManager rankManager;
    private static final List<String> SUB_COMMANDS = Arrays.asList("set", "get", "list", "group", "toggleeffects");
    private static final List<String> BASE_RANKS = Arrays.asList("op", "member", "visitor");

    public RankCommands(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("darkpixel.admin")) {
            sender.sendMessage("§c需要管理员权限！");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§c用法: /rank <set|get|list|group|toggleeffects> [参数]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set":
                if (args.length < 4) {
                    sender.sendMessage("§c用法: /rank set <玩家> <等级> <分数>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家 " + args[1] + " 不在线！");
                    return true;
                }
                String rank = args[2];
                int score;
                try {
                    score = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c分数必须是整数！");
                    return true;
                }
                rankManager.setRank(target, rank, score);
                sender.sendMessage("§a已将 " + target.getName() + " 的等级设置为 " + rank + "，分数: " + score);
                return true;

            case "get":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /rank get <玩家>");
                    return true;
                }
                Player targetGet = Bukkit.getPlayer(args[1]);
                if (targetGet == null) {
                    sender.sendMessage("§c玩家 " + args[1] + " 不在线！");
                    return true;
                }
                String currentRank = rankManager.getRank(targetGet);
                int currentScore = rankManager.getScore(targetGet);
                sender.sendMessage("§a" + targetGet.getName() + " 的等级: " + currentRank + "，分数: " + currentScore);
                return true;

            case "list":
                Map<UUID, PlayerRank> allRanks = rankManager.getAllRanks();
                if (allRanks.isEmpty()) {
                    sender.sendMessage("§c暂无 Rank 记录！");
                    return true;
                }
                sender.sendMessage("§aRank 列表:");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                for (Map.Entry<UUID, PlayerRank> entry : allRanks.entrySet()) {
                    PlayerRank pr = entry.getValue();
                    String lastModified = sdf.format(new Date(pr.getLastModified()));
                    sender.sendMessage("§e" + pr.getName() + " - Rank: " + pr.getRank() + "，分数: " + pr.getScore() + " (修改时间: " + lastModified + ")");
                }
                return true;

            case "group":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /rank group <add|remove|create|edit|delete> [参数]");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "add":
                        if (args.length < 4) {
                            sender.sendMessage("§c用法: /rank group add <玩家> <组名>");
                            return true;
                        }
                        Player addTarget = Bukkit.getPlayer(args[2]);
                        if (addTarget == null) {
                            sender.sendMessage("§c玩家 " + args[2] + " 不在线！");
                            return true;
                        }
                        rankManager.addPlayerToGroup(addTarget, args[3]);
                        sender.sendMessage("§a已将 " + addTarget.getName() + " 添加到组 " + args[3]);
                        return true;

                    case "remove":
                        if (args.length < 4) {
                            sender.sendMessage("§c用法: /rank group remove <玩家> <组名>");
                            return true;
                        }
                        Player removeTarget = Bukkit.getPlayer(args[2]);
                        if (removeTarget == null) {
                            sender.sendMessage("§c玩家 " + args[2] + " 不在线！");
                            return true;
                        }
                        rankManager.removePlayerFromGroup(removeTarget, args[3]);
                        sender.sendMessage("§a已将 " + removeTarget.getName() + " 从组 " + args[3] + "移除");
                        return true;

                    case "create":
                        if (args.length < 3) {
                            sender.sendMessage("§c用法: /rank group create <组名>");
                            return true;
                        }
                        rankManager.addGroup(args[2]);
                        sender.sendMessage("§a已创建身份组 " + args[2]);
                        return true;

                    case "edit":
                        if (args.length < 6) {
                            sender.sendMessage("§c用法: /rank group edit <组名> <颜色> <Emoji> <名牌>");
                            return true;
                        }
                        rankManager.updateGroup(args[2], args[3], args[4], args[5]);
                        sender.sendMessage("§a已更新身份组 " + args[2] + " 的属性");
                        return true;

                    case "delete":
                        if (args.length < 3) {
                            sender.sendMessage("§c用法: /rank group delete <组名>");
                            return true;
                        }
                        rankManager.removeGroup(args[2]);
                        sender.sendMessage("§a已删除身份组 " + args[2]);
                        return true;
                }
                return true;

            case "toggleeffects":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /rank toggleeffects <玩家>");
                    return true;
                }
                Player effectsTarget = Bukkit.getPlayer(args[1]);
                if (effectsTarget == null) {
                    sender.sendMessage("§c玩家 " + args[1] + " 不在线！");
                    return true;
                }
                rankManager.toggleEffects(effectsTarget);
                sender.sendMessage("§a已切换 " + effectsTarget.getName() + " 的特效状态");
                return true;

            default:
                sender.sendMessage("§c未知子命令！可用: set, get, list, group, toggleeffects");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("darkpixel.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return SUB_COMMANDS.stream().filter(cmd -> cmd.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            Set<String> customRanks = rankManager.getAllRanks().values().stream().map(PlayerRank::getRank).collect(Collectors.toSet());
            customRanks.addAll(BASE_RANKS);
            return customRanks.stream().filter(rank -> rank.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("group")) {
            return Arrays.asList("add", "remove", "create", "edit", "delete").stream().filter(cmd -> cmd.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("group") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("edit") || args[1].equalsIgnoreCase("delete")))) {
            return rankManager.getGroups().keySet().stream().filter(group -> group.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}