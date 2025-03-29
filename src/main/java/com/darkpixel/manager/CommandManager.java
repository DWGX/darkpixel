package com.darkpixel.manager;

import com.darkpixel.Global;
import com.darkpixel.ai.AiChatCommands;
import com.darkpixel.combat.bringBackBlocking.GiveBlockableSword;
import com.darkpixel.npc.NpcHandler;
import com.darkpixel.rank.RankCommands;
import com.darkpixel.utils.PingUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public class CommandManager implements CommandExecutor {
    private final Global context;
    private final GiveBlockableSword giveBlockableSword;
    private final BanManager banManager;

    public CommandManager(Global context) {
        this.context = context;
        this.giveBlockableSword = new GiveBlockableSword();
        this.banManager = new BanManager(context);
        JavaPlugin plugin = context.getPlugin();

        registerCommand(plugin, "aichat", new AiChatCommands(context.getAiChat(), context.getDashboard()));
        registerCommand(plugin, "giveblockablesword", this);
        registerCommand(plugin, "dashboard", context.getDashboard());
        registerCommand(plugin, "hub", context.getDashboard());
        registerCommand(plugin, "geiwoqian", this);
        registerCommand(plugin, "npc", context.getNpcHandler());
        registerCommand(plugin, "freeze", context.getPlayerFreeze());
        registerCommand(plugin, "setchattimes", this);
        registerCommand(plugin, "sit", this);
        registerCommand(plugin, "togglesit", this);
        registerCommand(plugin, "ping", this);
        registerCommand(plugin, "getswitchchest", context.getServerSwitchChest());
        registerCommand(plugin, "getradio", context.getServerRadioChest());
        registerCommand(plugin, "rank", new RankCommands(context));
        registerCommand(plugin, "darkban", this);
    }

    private void registerCommand(JavaPlugin plugin, String commandName, CommandExecutor executor) {
        if (plugin.getCommand(commandName) != null) {
            plugin.getCommand(commandName).setExecutor(executor);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "darkban":
                if (!sender.hasPermission("darkpixel.admin")) {
                    sender.sendMessage("§c需要管理员权限！");
                    return true;
                }
                if (args.length < 1) {
                    sender.sendMessage("§c用法: /darkban ban <玩家> <时间(分钟，-1为永久)> [原因] 或 /darkban unban <玩家>");
                    return true;
                }
                if (args[0].equalsIgnoreCase("ban")) {
                    if (args.length < 3) {
                        sender.sendMessage("§c用法: /darkban ban <玩家> <时间(分钟，-1为永久)> [原因]");
                        return true;
                    }
                    String playerName = args[1];
                    long banTime = Long.parseLong(args[2]);
                    String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "未指定原因";
                    long banUntil = banTime == -1 ? -1 : (banTime > 0 ? System.currentTimeMillis() + banTime * 60000 : 0);
                    banManager.banPlayer(playerName, banUntil, reason);
                    sender.sendMessage("§a已封禁 " + playerName + (banTime == -1 ? " 永久" : " " + banTime + " 分钟") + "，原因：" + reason);
                } else if (args[0].equalsIgnoreCase("unban")) {
                    if (args.length != 2) {
                        sender.sendMessage("§c用法: /darkban unban <玩家>");
                        return true;
                    }
                    banManager.unbanPlayer(args[1]);
                    sender.sendMessage("§a已解禁 " + args[1]);
                }
                return true;
            case "giveblockablesword":
                return giveBlockableSword.onCommand(sender, command, label, args);
            case "geiwoqian":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c仅玩家可用！");
                    return true;
                }
                sender.sendMessage("§c别想了，服务器不发钱！");
                return true;
            case "setchattimes":
                if (!sender.hasPermission("darkpixel.admin")) {
                    sender.sendMessage("§c需要管理员权限！");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage("§c用法: /setchattimes <玩家> <次数>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage("§c玩家 " + args[0] + " 不在线！");
                    return true;
                }
                int times = Integer.parseInt(args[1]);
                Global.executor.submit(() -> context.getAiChat().setPlayerMessageLimit(target.getName(), times));
                sender.sendMessage("§a已将 " + target.getName() + " 的聊天次数设置为 " + times);
                return true;
            case "togglesit":
                if (!sender.hasPermission("darkpixel.admin")) {
                    sender.sendMessage("§c需要管理员权限！");
                    return true;
                }
                boolean enabled = !context.getConfigManager().isSittingEnabled();
                context.getConfigManager().getConfig().set("sitting.enabled", enabled);
                Global.executor.submit(() -> context.getConfigManager().saveConfig("config.yml"));
                context.updateSitUtils();
                sender.sendMessage("§a坐下功能已" + (enabled ? "开启" : "关闭"));
                return true;
            case "sit":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c仅玩家可用！");
                    return true;
                }
                if (!context.getConfigManager().isSittingEnabled()) {
                    sender.sendMessage("§c坐下功能当前已被禁用！");
                    return true;
                }
                if (args.length == 0) {
                    Block blockBelow = player.getLocation().subtract(0, 1, 0).getBlock();
                    if (blockBelow.getType() != Material.AIR) {
                        context.getSitUtils().sitDown(player, blockBelow, false);
                        sender.sendMessage("§a你已坐下！");
                    } else {
                        sender.sendMessage("§c请站在一个方块上！");
                    }
                } else if (args[0].equalsIgnoreCase("toggle")) {
                    context.getConfigManager().toggleSittingPermission(player);
                    Global.executor.submit(() -> context.getConfigManager().saveConfig("config.yml"));
                    sender.sendMessage("§a坐下权限已切换为: " + context.getConfigManager().getSittingPermission(player));
                }
                return true;
            case "ping":
                target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : (sender instanceof Player ? (Player) sender : null);
                if (target == null) {
                    sender.sendMessage("§c玩家不在线或未指定！");
                    return true;
                }
                sender.sendMessage(PingUtils.formatPingMessage(target.getName(), target.getPing()));
                return true;
        }
        return false;
    }
}