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
public class SupplyStation implements Listener, CommandExecutor {
    private final Global context; 
    public SupplyStation(Global context) {
        this.context = context;
        context.getPlugin().getServer().getPluginManager().registerEvents(this, context.getPlugin());
        context.getPlugin().getCommand("geiwoqian").setExecutor(this);
    }
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        openSupplyInventory(p);
        return true;
    }
    private void openSupplyInventory(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§l物资补给站");
        inv.setItem(13, createGoldenApple());
        p.openInventory(inv);
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals("§l物资补给站")) return;
        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemStack clone = item.clone();
        clone.setAmount(1);
        p.getInventory().addItem(clone);
        p.sendMessage("§a已获取 " + item.getItemMeta().getDisplayName());
    }
    private ItemStack createGoldenApple() {
        ItemStack goldenApple = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta meta = goldenApple.getItemMeta();
        meta.setDisplayName("§e金苹果");
        meta.setLore(Arrays.asList("§7点击获取"));
        goldenApple.setItemMeta(meta);
        return goldenApple;
    }
}