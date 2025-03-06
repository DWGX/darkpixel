package com.darkpixel.combat.bringBackBlocking;
import de.tr7zw.nbtapi.NBT;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
public class GiveBlockableSword implements TabExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("darkpixel.givesword")) {
            s.sendMessage("§c无权限");
            return true;
        }
        if (a.length != 2) {
            s.sendMessage("§c/giveblockablesword <玩家> <剑>");
            return true;
        }
        Player p = Bukkit.getPlayer(a[0]);
        if (p == null) {
            s.sendMessage("§c玩家未找到");
            return true;
        }
        Material swordType = Material.getMaterial(a[1].toUpperCase());
        if (swordType == null || !isSwordType(swordType)) {
            s.sendMessage("§c无效的剑类型");
            return true;
        }
        ItemStack sword = new ItemStack(swordType);
        NBT.modifyComponents(sword, nbt -> {
            nbt.getOrCreateCompound("minecraft:consumable").setString("animation", "block");
            nbt.getOrCreateCompound("minecraft:consumable").setInteger("consume_seconds", 72000);
        });
        p.getInventory().addItem(sword);
        s.sendMessage("§a给予" + p.getName() + "可格挡" + a[1]);
        return true;
    }
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        ArrayList<String> comp = new ArrayList<>();
        if (!s.hasPermission("darkpixel.givesword")) return comp;
        if (a.length == 1) Bukkit.getOnlinePlayers().forEach(p -> comp.add(p.getName()));
        if (a.length == 2) comp.addAll(Arrays.asList("netherite_sword", "diamond_sword", "golden_sword", "iron_sword", "stone_sword", "wooden_sword"));
        return comp;
    }
    private boolean isSwordType(Material type) {
        return type == Material.NETHERITE_SWORD || type == Material.DIAMOND_SWORD ||
                type == Material.IRON_SWORD || type == Material.GOLDEN_SWORD ||
                type == Material.STONE_SWORD || type == Material.WOODEN_SWORD;
    }
}