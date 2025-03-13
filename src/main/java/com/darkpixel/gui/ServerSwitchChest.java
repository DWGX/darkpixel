package com.darkpixel.gui;

import com.darkpixel.Global;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerSwitchChest implements Listener, CommandExecutor {
    private final Global context;
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_COOLDOWN = 500;

    public ServerSwitchChest(Global context) {
        this.context = context;
        context.getPlugin().getServer().getPluginManager().registerEvents(this, context.getPlugin());
        context.getPlugin().getCommand("getswitchchest").setExecutor(this);
        context.getPlugin().getServer().getMessenger().registerOutgoingPluginChannel(context.getPlugin(), "BungeeCord");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令！");
            return true;
        }
        if (!player.hasPermission("darkpixel.switchchest")) {
            player.sendMessage("§c你没有权限使用此命令！");
            return true;
        }
        openSwitchChest(player);
        return true;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof org.bukkit.entity.Zombie zombie && zombie.hasMetadata("switchChest")) {
            Player player = e.getPlayer();
            openSwitchChest(player);
            e.setCancelled(true);
        }
    }

    private void openSwitchChest(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, "§l切服箱");
        inv.setItem(10, createNetherStar());   // 主城移到10号槽
        inv.setItem(12, createRedBed());      // 起床战争移到12号槽
        inv.setItem(14, createTunnelRats());  // Tunnel Rats移到14号槽
        inv.setItem(16, createSurviveItem()); // 生存服务器添加到16号槽
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§l切服箱")) return;

        long currentTime = System.currentTimeMillis();
        long lastTime = lastClickTime.getOrDefault(player.getUniqueId(), 0L);
        if (currentTime - lastTime < CLICK_COOLDOWN) return;
        lastClickTime.put(player.getUniqueId(), currentTime);

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String itemName = item.getItemMeta().getDisplayName();
        if (itemName.equals("§e主城")) {
            switchServer(player, "lobby");
            player.sendMessage("§a正在切换至主城服务器...");
        } else if (itemName.equals("§c起床战争")) {
            switchServer(player, "bedwars");
            player.sendMessage("§a正在切换至起床战争服务器...");
        } else if (itemName.equals("§dTunnel Rats")) {
            switchServer(player, "tunnelrats");
            player.sendMessage("§a正在切换至Tunnel Rats服务器...");
        } else if (itemName.equals("§b生存服务器")) {
            switchServer(player, "survive");
            player.sendMessage("§a正在切换至生存服务器...");
        }
        player.closeInventory();
    }

    private void switchServer(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(context.getPlugin(), "BungeeCord", out.toByteArray());
    }

    private ItemStack createNetherStar() {
        ItemStack netherStar = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = netherStar.getItemMeta();
        meta.setDisplayName("§e主城");
        meta.setLore(Arrays.asList("§7点击切换至主城服务器"));
        netherStar.setItemMeta(meta);
        return netherStar;
    }

    private ItemStack createRedBed() {
        ItemStack redBed = new ItemStack(Material.RED_BED);
        ItemMeta meta = redBed.getItemMeta();
        meta.setDisplayName("§c起床战争");
        meta.setLore(Arrays.asList("§7点击切换至起床战争服务器"));
        redBed.setItemMeta(meta);
        return redBed;
    }

    private ItemStack createTunnelRats() {
        ItemStack tunnelRatsItem = new ItemStack(Material.RABBIT_FOOT);
        ItemMeta meta = tunnelRatsItem.getItemMeta();
        meta.setDisplayName("§dTunnel Rats");
        meta.setLore(Arrays.asList("§7点击切换至Tunnel Rats服务器"));
        tunnelRatsItem.setItemMeta(meta);
        return tunnelRatsItem;
    }

    private ItemStack createSurviveItem() {
        ItemStack surviveItem = new ItemStack(Material.DIAMOND_PICKAXE); // 使用钻石镐作为生存服务器图标
        ItemMeta meta = surviveItem.getItemMeta();
        meta.setDisplayName("§b生存服务器");
        meta.setLore(Arrays.asList("§7点击切换至生存服务器"));
        surviveItem.setItemMeta(meta);
        return surviveItem;
    }
}