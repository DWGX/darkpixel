package com.darkpixel.gui;

import com.darkpixel.Global;
import com.darkpixel.rank.RankData;
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
        RankData rankData = rankManager.getAllRanks().getOrDefault(player.getUniqueId(), new RankData("member", 0));
        long last_sign_in = getlast_sign_in(player);
        int sign_in_count = getsign_in_count(player);
        long currentTime = System.currentTimeMillis();
        boolean canSignIn = currentTime - last_sign_in >= SIGN_IN_COOLDOWN;

        ItemStack signInButton = new ItemStack(canSignIn ? Material.EMERALD : Material.REDSTONE);
        ItemMeta meta = signInButton.getItemMeta();
        meta.setDisplayName(canSignIn ? "§a点击签到" : "§c已签到，明天再来");
        meta.setLore(Arrays.asList("§7签到次数: " + sign_in_count,
                "§7上次签到: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(last_sign_in))));
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

        RankData rankData = rankManager.getAllRanks().getOrDefault(player.getUniqueId(), new RankData("member", 0));
        long currentTime = System.currentTimeMillis();
        long last_sign_in = getlast_sign_in(player);
        int sign_in_count = getsign_in_count(player);

        if (currentTime - last_sign_in < SIGN_IN_COOLDOWN) {
            player.sendMessage("§c你今天已经签到过了，请明天再来！");
            return;
        }

        setlast_sign_in(player, currentTime);
        int newsign_in_count = sign_in_count + 1;
        setsign_in_count(player, newsign_in_count);
        int newScore = rankData.getScore() + 10;
        rankManager.setRank(player, rankData.getRank(), newScore, rankManager.getJoinParticle(player), rankManager.getJoinMessage(player));
        player.sendMessage("§a签到成功！签到次数: " + newsign_in_count + "，分数 +10，新分数: " + newScore);
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
    }

    private long getlast_sign_in(Player player) {
        return context.getPlayerData().getlast_sign_in(player);
    }

    private void setlast_sign_in(Player player, long time) {
        context.getPlayerData().setlast_sign_in(player, time);
    }

    private int getsign_in_count(Player player) {
        return context.getPlayerData().getsign_in_count(player);
    }

    private void setsign_in_count(Player player, int count) {
        context.getPlayerData().setsign_in_count(player, count);
    }
}