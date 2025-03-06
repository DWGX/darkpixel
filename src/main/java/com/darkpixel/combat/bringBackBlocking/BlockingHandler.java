package com.darkpixel.combat.bringBackBlocking;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.darkpixel.manager.ConfigManager;
import de.tr7zw.nbtapi.NBT;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;
public class BlockingHandler implements Listener {
    private final List<Player> blocking = new ArrayList<>();
    private final ConfigManager configManager;
    public BlockingHandler(JavaPlugin plugin) {
        this.configManager = new ConfigManager(plugin);
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG) {
                    @Override
                    public void onPacketReceiving(PacketEvent e) {
                        blocking.remove(e.getPlayer());
                    }
                });
    }
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !blocking.contains(p)) return;
        if (configManager.getConfig().getBoolean("blocking.reduce-only-entity-damage") &&
                e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.AIR && isBlockableItem(item)) {
            e.setDamage(configManager.getConfig().getInt("blocking.damage-reduce-percentage", 50) * e.getDamage() / 100);
        }
    }
    @EventHandler
    public void onBlock(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
        if (isBlockableItem(item)) {
            blocking.add(e.getPlayer());
            if (configManager.isVanillaBlockingEnabled() && isSword(item) && !hasBlockAnimation(item)) {
                NBT.modifyComponents(item, nbt -> {
                    nbt.getOrCreateCompound("minecraft:consumable").setString("animation", "block");
                    nbt.getOrCreateCompound("minecraft:consumable").setInteger("consume_seconds", 72000);
                });
            }
        }
    }
    private boolean isBlockableItem(ItemStack item) {
        return (isSword(item) && configManager.isVanillaBlockingEnabled()) || hasBlockAnimation(item);
    }
    private boolean isSword(ItemStack item) {
        return item.getType().name().endsWith("_SWORD");
    }
    private boolean hasBlockAnimation(ItemStack item) {
        return NBT.getComponents(item, nbt ->
                nbt.hasTag("minecraft:consumable") &&
                        "block".equals(nbt.getCompound("minecraft:consumable").getString("animation")));
    }
}