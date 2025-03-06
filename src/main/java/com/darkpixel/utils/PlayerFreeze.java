package com.darkpixel.utils;
import com.darkpixel.Global;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
public class PlayerFreeze implements Listener, CommandExecutor {
    private final Global context;
    private final Set<Player> frozenPlayers = Collections.synchronizedSet(new HashSet<>());
    public PlayerFreeze(Global context) {
        this.context = context;
        loadFrozenPlayers();
    }
    public void reloadConfig() {
        loadFrozenPlayers();
        LogUtil.info("PlayerFreeze 配置已重新加载");
    }
    private void loadFrozenPlayers() {
        if (!context.getConfig().getBoolean("freeze.persist_across_relog", false)) return;
        for (String uuid : context.getConfig().getStringList("frozen_players")) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                freezePlayer(player, false);
            }
        }
    }
    private void saveFrozenPlayers() {
        if (!context.getConfig().getBoolean("freeze.persist_across_relog", false)) return;
        context.getConfig().set("frozen_players", frozenPlayers.stream()
                .map(player -> player.getUniqueId().toString())
                .toList());
        context.getConfigManager().saveConfig("config.yml"); 
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("darkpixel.admin")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§c用法: /freeze <player> <on|off>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§c玩家 " + args[0] + " 不在线！");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "on":
                freezePlayer(target, true);
                sender.sendMessage("§a已冻结玩家 " + target.getName());
                target.sendMessage("§c你已被管理员冻结！");
                break;
            case "off":
                unfreezePlayer(target);
                sender.sendMessage("§a已解冻玩家 " + target.getName());
                target.sendMessage("§a你已解除冻结！");
                break;
            default:
                sender.sendMessage("§c无效参数: 使用 'on' 或 'off'");
        }
        return true;
    }
    private void freezePlayer(Player player, boolean save) {
        frozenPlayers.add(player);
        player.setMetadata("frozen", new FixedMetadataValue(context.getPlugin(), true));
        if (save) saveFrozenPlayers();
    }
    private void unfreezePlayer(Player player) {
        frozenPlayers.remove(player);
        player.removeMetadata("frozen", context.getPlugin());
        saveFrozenPlayers();
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.contains(player)) {
            event.setTo(event.getFrom());
            player.sendMessage("§c你已被冻结，无法移动！");
        }
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (context.getConfig().getBoolean("freeze.persist_across_relog", false) &&
                context.getConfig().getStringList("frozen_players").contains(player.getUniqueId().toString())) {
            freezePlayer(player, false);
            player.sendMessage("§c你仍处于冻结状态！");
        }
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!context.getConfig().getBoolean("freeze.persist_across_relog", false)) {
            unfreezePlayer(player);
        }
    }
    public Set<Player> getFrozenPlayers() {
        return frozenPlayers;
    }
}