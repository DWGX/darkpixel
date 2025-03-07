package com.darkpixel.utils;
import com.darkpixel.Global;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import java.util.HashMap;
import java.util.Map;
public class SitUtils implements Listener {
    private final Global context;
    private final Map<Player, ArmorStand> sittingPlayers = new HashMap<>();
    public SitUtils(Global context) {
        this.context = context;
    }
    public void reloadConfig() {
        LogUtil.info("SitUtils 配置已重新加载");
    }
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!context.getConfigManager().isSittingEnabled() || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null || player.isSneaking() || sittingPlayers.containsKey(player)) return;
        if (!context.getConfigManager().isSittingOnBlocksAllowed() ||
                context.getConfigManager().getSittingBlockedWorlds().contains(player.getWorld().getName())) return;
        String blockName = block.getType().name().toLowerCase();
        if (!context.getConfigManager().getValidSittingBlocks().stream().anyMatch(blockName::contains)) return;
        event.setCancelled(true);
        sitDown(player, block.getLocation().add(0.5, -0.5, 0.5));
    }
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!context.getConfigManager().isSittingEnabled() || !context.getConfigManager().isSittingOnPlayersAllowed()) return;
        Player player = event.getPlayer();
        Entity target = event.getRightClicked();
        if (!(target instanceof Player targetPlayer) || sittingPlayers.containsKey(player)) return;
        if (!context.getConfigManager().canBeSatOn(targetPlayer) ||
                context.getConfigManager().getSittingBlockedWorlds().contains(player.getWorld().getName())) return;
        event.setCancelled(true);
        // 调整为目标玩家的头部位置
        Location seatLocation = targetPlayer.getLocation().clone().add(0, 1.8, 0); // 1.8 是玩家的身高
        sitDown(player, seatLocation);
    }
    public void sitDown(Player player, Location seatLocation) {
        sitDown(player, seatLocation, false);
    }
    public void sitDown(Player player, Block block, boolean adjustHeight) {
        Location seatLocation = block.getLocation().add(0.5, adjustHeight ? -0.5 : 0, 0.5);
        sitDown(player, seatLocation, true);
    }
    private void sitDown(Player player, Location seatLocation, boolean fromBlock) {
        double offset = fromBlock ? context.getConfigManager().getSittingHeightOffsetBlocks()
                : context.getConfigManager().getSittingHeightOffsetPlayers();
        ArmorStand seat = (ArmorStand) player.getWorld().spawnEntity(
                seatLocation.clone().subtract(0, offset, 0),
                EntityType.ARMOR_STAND
        );
        seat.setGravity(false);
        seat.setVisible(false);
        seat.setMarker(true);
        seat.setMetadata("SitUtilsSeat", new FixedMetadataValue(context.getPlugin(), true));
        seat.addPassenger(player);
        sittingPlayers.put(player, seat);
        player.sendMessage("§a你已坐下！右键地面或跳跃以起身。");
    }
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player) || !sittingPlayers.containsKey(player)) return;
        ArmorStand seat = sittingPlayers.remove(player);
        if (seat != null && seat.hasMetadata("SitUtilsSeat")) {
            seat.remove();
            player.sendMessage("§a你已起身！");
        }
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ArmorStand seat = sittingPlayers.remove(player);
        if (seat != null && seat.hasMetadata("SitUtilsSeat")) {
            seat.remove();
        }
    }
    public Map<Player, ArmorStand> getSittingPlayers() {
        return sittingPlayers;
    }
}