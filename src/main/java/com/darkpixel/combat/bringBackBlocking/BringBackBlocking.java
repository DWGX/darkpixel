package com.darkpixel.combat.bringBackBlocking;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.darkpixel.Global;
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTCompound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
public class BringBackBlocking implements Listener {
    private final List<Player> blocking = new ArrayList<>();
    private final Global context;
    public BringBackBlocking(Global context) {
        this.context = context;
        Bukkit.getLogger().info("[Blocking] 系统启用");
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(context.getPlugin(), ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG) {
                    @Override
                    public void onPacketReceiving(PacketEvent e) {
                        blocking.remove(e.getPlayer());
                    }
                });
    }
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (context.getConfig().getBoolean("blocking.reduce-only-entity-damage") &&
                e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;
        if (!isBlockableSword(item) && !(context.getConfig().getBoolean("blocking.enable-vanilla-blocking") && isSword(item))) return;
        if (blocking.contains(p)) e.setDamage(context.getConfig().getInt("blocking.damage-reduce-percentage") * e.getDamage() / 100);
    }
    @EventHandler
    public void onBlock(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (isBlockableSword(item) || (context.getConfig().getBoolean("blocking.enable-vanilla-blocking") && isSword(item))) {
            blocking.add(p);
            if (context.getConfig().getBoolean("blocking.enable-vanilla-blocking") && isSword(item) && !isBlockableSword(item)) {
                NBT.modifyComponents(item, nbt -> {
                    NBTCompound con = (NBTCompound) nbt.getOrCreateCompound("minecraft:consumable");
                    con.setString("animation", "block");
                    con.setInteger("consume_seconds", 72000);
                });
            }
        }
    }
    private boolean isSword(ItemStack item) {
        Material t = item.getType();
        return t == Material.NETHERITE_SWORD || t == Material.DIAMOND_SWORD ||
                t == Material.IRON_SWORD || t == Material.GOLDEN_SWORD ||
                t == Material.STONE_SWORD || t == Material.WOODEN_SWORD;
    }
    private boolean isBlockableSword(ItemStack item) {
        if (!isSword(item)) return false;
        AtomicBoolean r = new AtomicBoolean(false);
        NBT.getComponents(item, nbt -> {
            if (nbt.hasTag("minecraft:consumable")) {
                NBTCompound c = (NBTCompound) nbt.getCompound("minecraft:consumable");
                if (c != null && "block".equals(c.getString("animation"))) r.set(true);
            }
        });
        return r.get();
    }
}