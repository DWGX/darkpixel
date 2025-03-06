package com.darkpixel.manager;
import com.darkpixel.Global;
import com.darkpixel.ai.AiChatCommands;
import com.darkpixel.combat.bringBackBlocking.GiveBlockableSword; 
import com.darkpixel.npc.NpcHandler;
import com.darkpixel.utils.PingUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
public class CommandManager implements CommandExecutor {
    private final Global context;
    private final GiveBlockableSword giveBlockableSword; 
    public CommandManager(Global context) {
        this.context = context;
        this.giveBlockableSword = new GiveBlockableSword(); 
        JavaPlugin plugin = context.getPlugin();
        plugin.getCommand("aichat").setExecutor(new AiChatCommands(context.getAiChat(), context.getDashboard()));
        plugin.getCommand("giveblockablesword").setExecutor(this);
        plugin.getCommand("dashboard").setExecutor(context.getDashboard());
        plugin.getCommand("hub").setExecutor(context.getDashboard());
        plugin.getCommand("geiwoqian").setExecutor(this);
        plugin.getCommand("npc").setExecutor(new NpcHandler(context.getConfigManager(), context.getDashboard()));
        plugin.getCommand("freeze").setExecutor(context.getPlayerFreeze());
        plugin.getCommand("setchattimes").setExecutor(this);
        plugin.getCommand("sit").setExecutor(this);
        plugin.getCommand("togglesit").setExecutor(this);
        plugin.getCommand("ping").setExecutor(this);
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "giveblockablesword":
                return giveBlockableSword.onCommand(sender, command, label, args);
            case "geiwoqian":
                return handleGeiWoQian(sender);
            case "setchattimes":
                return handleSetChatTimes(sender, args);
            case "togglesit":
                if (!sender.hasPermission("darkpixel.admin")) {
                    sender.sendMessage("§c需要管理员权限！");
                    return true;
                }
                boolean enabled = !context.getConfigManager().getConfig("config.yml").getBoolean("sitting.enabled", true);
                context.getConfigManager().getConfig("config.yml").set("sitting.enabled", enabled);
                context.getConfigManager().saveConfig("config.yml");
                sender.sendMessage("§a坐下功能已" + (enabled ? "开启" : "关闭"));
                return true;
            case "sit":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c仅玩家可用！");
                    return true;
                }
                if (args.length == 0) {
                    Block blockBelow = player.getLocation().subtract(0, 1, 0).getBlock();
                    if (blockBelow.getType() != Material.AIR) {
                        context.getSitUtils().sitDown(player, blockBelow, true);
                        sender.sendMessage("§a你已坐下！");
                    } else {
                        sender.sendMessage("§c请站在一个方块上！");
                    }
                } else if (args[0].equalsIgnoreCase("toggle")) {
                    context.getConfigManager().toggleSittingPermission(player);
                    context.getConfigManager().saveConfig("config.yml");
                    sender.sendMessage("§a坐下权限已切换为: " + context.getConfigManager().getSittingPermission(player));
                }
                return true;
            case "ping":
                return handlePing(sender, args);
        }
        return false;
    }
    private boolean handleGeiWoQian(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c仅玩家可用！");
            return true;
        }
        player.sendMessage("§c别想了，服务器不发钱！");
        return true;
    }
    private boolean handleSetChatTimes(CommandSender sender, String[] args) {
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
        try {
            int times = Integer.parseInt(args[1]);
            context.getAiChat().setPlayerMessageLimit(target.getName(), times);
            sender.sendMessage("§a已将 " + target.getName() + " 的聊天次数设置为 " + times);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c次数必须是整数！");
        }
        return true;
    }
    private boolean handlePing(CommandSender sender, String[] args) {
        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : (sender instanceof Player ? (Player) sender : null);
        if (target == null) {
            sender.sendMessage("§c玩家不在线或未指定！");
            return true;
        }
        sender.sendMessage(PingUtils.formatPingMessage(target.getName(), target.getPing()));
        return true;
    }
}