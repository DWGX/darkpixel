package com.darkpixel.gui;

import com.darkpixel.Global;
import com.darkpixel.rank.PlayerRank;
import com.darkpixel.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SignInContainer implements Listener {
    private final Global context;
    private final RankManager rankManager;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private static final long SIGN_IN_COOLDOWN = 24 * 60 * 60 * 1000;

    public SignInContainer(Global context, RankManager rankManager) {
        this.context = context;
        this.rankManager = rankManager;
        context.getPlugin().getServer().getPluginManager().registerEvents(this, context.getPlugin());
        Bukkit.getCommandMap().register("signin", new org.bukkit.command.defaults.BukkitCommand("signin") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c仅玩家可用！");
                    return true;
                }
                openSignInInventory((Player) sender);
                return true;
            }
        });
    }

    private void openSignInInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§a每日签到");
        PlayerRank playerRank = rankManager.getAllRanks().getOrDefault(player.getUniqueId(), new PlayerRank(player.getName()));
        long currentTime = System.currentTimeMillis();
        boolean canSignIn = currentTime - playerRank.getLastSignIn() >= SIGN_IN_COOLDOWN;

        ItemStack signInButton = new ItemStack(canSignIn ? Material.EMERALD : Material.REDSTONE);
        ItemMeta meta = signInButton.getItemMeta();
        meta.setDisplayName(canSignIn ? "§a点击签到" : "§c已签到，明天再来");
        meta.setLore(Arrays.asList("§7签到次数: " + playerRank.getSignInCount(), "§7上次签到: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(playerRank.getLastSignIn()))));
        signInButton.setItemMeta(meta);
        inv.setItem(13, signInButton);

        player.openInventory(inv);
        openInventories.put(player.getUniqueId(), inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!openInventories.containsKey(player.getUniqueId()) || !event.getInventory().equals(openInventories.get(player.getUniqueId()))) return;

        event.setCancelled(true);
        if (event.getSlot() != 13) return;

        PlayerRank playerRank = rankManager.getAllRanks().getOrDefault(player.getUniqueId(), new PlayerRank(player.getName()));
        long currentTime = System.currentTimeMillis();
        if (currentTime - playerRank.getLastSignIn() < SIGN_IN_COOLDOWN) {
            player.sendMessage("§c你今天已经签到过了，请明天再来！");
            return;
        }

        playerRank.setLastSignIn(currentTime);
        playerRank.setSignInCount(playerRank.getSignInCount() + 1);
        playerRank.setScore(playerRank.getScore() + 10);
        rankManager.saveRanks();
        player.sendMessage("§a签到成功！签到次数: " + playerRank.getSignInCount() + "，分数 +10，新分数: " + playerRank.getScore());
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
    }
}