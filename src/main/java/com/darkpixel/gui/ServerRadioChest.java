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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public class ServerRadioChest implements Listener, CommandExecutor {
    private final Global context;

    public ServerRadioChest(Global context) {
        this.context = context;
        context.getPlugin().getServer().getPluginManager().registerEvents(this, context.getPlugin());
        context.getPlugin().getCommand("getradio").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令！");
            return true;
        }
        if (!player.hasPermission("darkpixel.radio")) {
            player.sendMessage("§c你没有权限使用此命令！");
            return true;
        }
        openRadioChest(player);
        return true;
    }

    private void openRadioChest(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, "§l服务器音乐切换箱");
        inv.setItem(10, createWhiteWool());
        inv.setItem(11, createGreenWool());
        inv.setItem(12, createYellowWool());
        inv.setItem(14, createGrayWool());
        inv.setItem(15, createCyanWool());
        inv.setItem(16, createRedWool());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§l服务器音乐切换箱")) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String itemName = item.getItemMeta().getDisplayName();
        switch (itemName) {
            case "§f关闭服务器广播":
                Bukkit.dispatchCommand(player, "serverradio disable server_radio");
                player.sendMessage("§a已关闭服务器广播");
                break;
            case "§a开启服务器广播":
                Bukkit.dispatchCommand(player, "serverradio enable server_radio");
                player.sendMessage("§a已开启服务器广播");
                break;
            case "§e跳过下一首服务器广播":
                Bukkit.dispatchCommand(player, "serverradio skip server_radio");
                player.sendMessage("§a已跳过下一首服务器广播");
                break;
            case "§7关闭位置广播":
                Bukkit.dispatchCommand(player, "positionradio disable radio");
                player.sendMessage("§a已关闭位置广播");
                break;
            case "§b开启位置广播":
                Bukkit.dispatchCommand(player, "positionradio enable radio");
                player.sendMessage("§a已开启位置广播");
                break;
            case "§c跳过位置广播":
                Bukkit.dispatchCommand(player, "positionradio skip radio");
                player.sendMessage("§a已跳过位置广播");
                break;
        }
        player.closeInventory();
    }

    private ItemStack createWhiteWool() {
        ItemStack wool = new ItemStack(Material.WHITE_WOOL);
        ItemMeta meta = wool.getItemMeta();
        meta.setDisplayName("§f关闭服务器广播");
        meta.setLore(Arrays.asList("§7点击关闭服务器广播"));
        wool.setItemMeta(meta);
        return wool;
    }

    private ItemStack createGreenWool() {
        ItemStack wool = new ItemStack(Material.GREEN_WOOL);
        ItemMeta meta = wool.getItemMeta();
        meta.setDisplayName("§a开启服务器广播");
        meta.setLore(Arrays.asList("§7点击开启服务器广播"));
        wool.setItemMeta(meta);
        return wool;
    }

    private ItemStack createYellowWool() {
        ItemStack wool = new ItemStack(Material.YELLOW_WOOL);
        ItemMeta meta = wool.getItemMeta();
        meta.setDisplayName("§e跳过下一首服务器广播");
        meta.setLore(Arrays.asList("§7点击跳过下一首服务器广播"));
        wool.setItemMeta(meta);
        return wool;
    }

    private ItemStack createGrayWool() {
        ItemStack wool = new ItemStack(Material.GRAY_WOOL);
        ItemMeta meta = wool.getItemMeta();
        meta.setDisplayName("§7关闭位置广播");
        meta.setLore(Arrays.asList("§7点击关闭位置广播"));
        wool.setItemMeta(meta);
        return wool;
    }

    private ItemStack createCyanWool() {
        ItemStack wool = new ItemStack(Material.CYAN_WOOL);
        ItemMeta meta = wool.getItemMeta();
        meta.setDisplayName("§b开启位置广播");
        meta.setLore(Arrays.asList("§7点击开启位置广播"));
        wool.setItemMeta(meta);
        return wool;
    }

    private ItemStack createRedWool() {
        ItemStack wool = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = wool.getItemMeta();
        meta.setDisplayName("§c跳过位置广播");
        meta.setLore(Arrays.asList("§7点击跳过位置广播"));
        wool.setItemMeta(meta);
        return wool;
    }
}