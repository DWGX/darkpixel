package com.darkpixel.ai;

import com.darkpixel.gui.DashboardHandler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AiChatCommands implements CommandExecutor, TabCompleter {
    private final AiChatHandler handler;
    private final DashboardHandler dashboardHandler;

    public AiChatCommands(AiChatHandler handler, DashboardHandler dashboardHandler) {
        this.handler = handler;
        this.dashboardHandler = dashboardHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家可用");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage("§e/ai <public|private> <消息> 或 /ai <mode|setmodel|whitelist|unwhitelist|setlimit|addlimit|history|adm> [参数]");
            return true;
        }
        String subCommand = args[0].toLowerCase();
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        switch (subCommand) {
            case "public":
            case "private":
                if (args.length < 2) {
                    p.sendMessage("§c请输入消息");
                    return true;
                }
                handler.sendMessage(p, message, subCommand.equals("public"));
                return true;
            case "mode":
            case "setmodel":
            case "whitelist":
            case "unwhitelist":
            case "setlimit":
            case "addlimit":
            case "history":
            case "adm":
                handler.handleAdminCommand(p, subCommand, message);
                return true;
            default:
                p.sendMessage("§c请使用 /ai public 或 /ai private 发送消息");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (!(sender instanceof Player)) return suggestions;
        boolean isAdmin = sender.hasPermission("darkpixel.admin");
        if (args.length == 1) {
            suggestions.addAll(Arrays.asList("public", "private"));
            if (isAdmin) suggestions.addAll(Arrays.asList("mode", "setmodel", "whitelist", "unwhitelist", "setlimit", "addlimit", "history", "adm"));
        } else if (args.length == 2 && isAdmin) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("mode") || subCommand.equals("setmodel")) {
                suggestions.addAll(Arrays.asList("deepseek-chat", "deepseek-reasoner", "deepseek-coder", "deepseek-pro"));
            } else if (subCommand.equals("setmodel") || subCommand.equals("whitelist") ||
                    subCommand.equals("unwhitelist") || subCommand.equals("setlimit")) {
                Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
            } else if (subCommand.equals("addlimit")) {
                suggestions.add("dashboard");
            } else if (subCommand.equals("history")) {
                suggestions.addAll(Arrays.asList("clear", "open", "stop", "export"));
            }
        }
        return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .distinct()
                .sorted()
                .toList();
    }
}