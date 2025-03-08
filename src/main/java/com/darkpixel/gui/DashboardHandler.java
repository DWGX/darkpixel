package com.darkpixel.gui;
import com.darkpixel.Global;
import com.darkpixel.ai.AiChatHandler;
import com.darkpixel.manager.ConfigManager;
import com.darkpixel.utils.LogUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import java.util.*;
public class DashboardHandler implements Listener, CommandExecutor, TabCompleter {
    private final Map<Player, Player> spectating = new HashMap<>();
    private final Map<Player, Integer> chatCounts = new HashMap<>();
    private final Map<Player, Long> lastClaimTime = new HashMap<>();
    private int dashboardChatLimit = 5;
    private final JavaPlugin plugin;
    private final AiChatHandler aiChat;
    private final ConfigManager configManager;
    private final Global context;
    private static final int[] GAME_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 35};
    private static final int CHAT_LIMIT_SLOT = 48;
    public DashboardHandler(JavaPlugin plugin, AiChatHandler aiChat, ConfigManager configManager, Global context) {
        this.plugin = plugin;
        this.aiChat = aiChat;
        this.configManager = configManager;
        this.context = context;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadChatCounts();
    }
    public void reloadConfig() {
        loadChatCounts();
        LogUtil.info("Dashboard 配置已重新加载");
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (cmd == null) {
            return false;
        }
        String commandName = cmd.getName();
        if (commandName == null) {
            return false;
        }
        if (commandName.equalsIgnoreCase("dashboard")) {
            openMainDashboard(player);
        } else if (commandName.equalsIgnoreCase("hub")) {
            resetPlayerState(player);
            player.performCommand("trigger hub");
            player.sendMessage("§a已返回大厅");
        }
        return true;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return Collections.emptyList();
    }
    public void openMainDashboard(Player player) {
        YamlConfiguration minigameConfig = context.getMinigameConfig();
        if (minigameConfig == null || !minigameConfig.contains("games")) {
            player.sendMessage("§c小游戏配置加载失败，请联系管理员！检查 minigame.yml 文件。");
            LogUtil.severe("小游戏配置加载失败：minigame.yml 可能缺失或格式错误。");
            return;
        }
        resetPlayerState(player);
        Inventory inventory = Bukkit.createInventory(player, 54, "§l服务大厅");
        populateGameItems(inventory, minigameConfig);
        addDashboardChatLimitItem(inventory, player);
        addPlayerListItem(inventory);
        addServerStatusItem(inventory, player);
        player.openInventory(inventory);
    }
    private void populateGameItems(Inventory inventory, YamlConfiguration minigameConfig) {
        int slotIndex = 0;
        for (String game : minigameConfig.getConfigurationSection("games").getKeys(false)) {
            if (slotIndex >= GAME_SLOTS.length) break;
            String materialName = minigameConfig.getString("games." + game + ".material", "STONE");
            String displayName = minigameConfig.getString("games." + game + ".display", "§c未定义名称");
            String lore = minigameConfig.getString("games." + game + ".lore", "§7未定义描述");
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                material = Material.STONE;
                LogUtil.warning("无效的材质 '" + materialName + "' 在游戏 '" + game + "' 中，使用默认值 STONE");
            }
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
                meta.setLore(Collections.singletonList(ChatColor.translateAlternateColorCodes('&', lore)));
                item.setItemMeta(meta);
            }
            inventory.setItem(GAME_SLOTS[slotIndex++], item);
        }
    }
    private void addDashboardChatLimitItem(Inventory inventory, Player viewer) {
        ItemStack chatLimit = new ItemStack(Material.PAPER);
        ItemMeta meta = chatLimit.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e获取发言次数: " + Math.max(dashboardChatLimit, 0));
            meta.setLore(Collections.singletonList("§7点击获取 1 次AI聊天次数（多人可抢）"));
            chatLimit.setItemMeta(meta);
        }
        inventory.setItem(CHAT_LIMIT_SLOT, chatLimit);
    }
    private void addPlayerListItem(Inventory inventory) {
        ItemStack players = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) players.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b在线玩家 (" + Bukkit.getOnlinePlayers().size() + ")");
            meta.setLore(Collections.singletonList("§7点击查看所有在线玩家"));
            players.setItemMeta(meta);
        }
        inventory.setItem(49, players);
    }
    private void addServerStatusItem(Inventory inventory, Player player) {
        ItemStack serverInfo = new ItemStack(Material.CLOCK);
        ItemMeta meta = serverInfo.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a服务器状态");
            double tps = Math.min(Bukkit.getTPS()[0], 20.0);
            int ping = player.getPing();
            String tpsColor = tps >= 18 ? "§a" : (tps >= 15 ? "§e" : "§c");
            String pingColor = ping <= 50 ? "§a" : (ping <= 100 ? "§e" : "§c");
            meta.setLore(Arrays.asList(
                    "§7服务器延迟 (TPS): " + tpsColor + String.format("%.2f", tps),
                    "§7你的延迟 (Ping): " + pingColor + ping + "ms"
            ));
            serverInfo.setItemMeta(meta);
        }
        inventory.setItem(50, serverInfo);
    }
    private void openPlayerList(Player player, int page) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.remove(player);
        int totalPages = (int) Math.ceil(onlinePlayers.size() / 45.0);
        Inventory inventory = Bukkit.createInventory(player, 54, "§l在线玩家 - 页 " + (page + 1));
        int slot = populatePlayerItems(inventory, onlinePlayers, page);
        if (page > 0) inventory.setItem(45, createNavigationItem(Material.ARROW, "§a上一页"));
        if (page < totalPages - 1) inventory.setItem(53, createNavigationItem(Material.ARROW, "§a下一页"));
        inventory.setItem(49, createNavigationItem(Material.BARRIER, "§c返回主菜单"));
        player.openInventory(inventory);
    }
    private int populatePlayerItems(Inventory inventory, List<Player> players, int page) {
        int slot = 0;
        for (int i = page * 45; i < Math.min((page + 1) * 45, players.size()); i++) {
            if (slot >= 45) break;
            inventory.setItem(slot++, createPlayerHead(players.get(i)));
        }
        return slot;
    }
    private ItemStack createPlayerHead(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName("§e" + target.getName());
            int ping = target.getPing();
            String pingColor = ping <= 50 ? "§a" : (ping <= 100 ? "§e" : "§c");
            meta.setLore(Arrays.asList(
                    "§7点击观战",
                    "§a坐标: §f" + target.getLocation().getBlockX() + ", " + target.getLocation().getBlockY() + ", " + target.getLocation().getBlockZ(),
                    "§a延迟: " + pingColor + ping + "ms"
            ));
            head.setItemMeta(meta);
        }
        return head;
    }
    private ItemStack createNavigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals("§l服务大厅") && !title.startsWith("§l在线玩家 - 页 ")) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        event.setCancelled(true);
        String strippedTitle = ChatColor.stripColor(title).trim();
        if (strippedTitle.equals("服务大厅")) {
            if (item.getType() == Material.PAPER && item.getItemMeta().getDisplayName().startsWith("§e获取发言次数")) {
                handleChatLimitClick(player);
            } else if (item.getType() == Material.PLAYER_HEAD && item.getItemMeta().getDisplayName().startsWith("§b在线玩家")) {
                openPlayerList(player, 0);
            } else {
                YamlConfiguration minigameConfig = context.getMinigameConfig();
                if (minigameConfig != null && minigameConfig.contains("games")) {
                    for (String game : minigameConfig.getConfigurationSection("games").getKeys(false)) {
                        Material material = Material.matchMaterial(minigameConfig.getString("games." + game + ".material", "STONE"));
                        if (material != null && item.getType() == material) {
                            handleGameClick(player, game, minigameConfig);
                            break;
                        }
                    }
                }
            }
        } else if (strippedTitle.startsWith("在线玩家 - 页 ")) {
            handlePlayerListClick(player, item);
        }
    }
    private void handleChatLimitClick(Player player) {
        synchronized (this) {
            if (dashboardChatLimit <= 0) {
                player.sendMessage("§cAI聊天次数已用完");
                return;
            }
            long currentTime = System.currentTimeMillis();
            Long lastTime = lastClaimTime.getOrDefault(player, 0L);
            if (currentTime - lastTime < 1000) {
                player.sendMessage("§c你领取太快了，请稍等片刻！");
                return;
            }
            dashboardChatLimit--;
            lastClaimTime.put(player, currentTime);
            int playerChatCount = chatCounts.getOrDefault(player, 0) + 1;
            chatCounts.put(player, playerChatCount);
            player.sendMessage("§a已获取 1 次AI聊天次数，剩余: " + dashboardChatLimit + "，你当前次数: " + playerChatCount);
            saveChatCounts();
            updateDashboard(player);
        }
    }
    private void handleGameClick(Player player, String game, YamlConfiguration minigameConfig) {
        resetPlayerState(player);
        if (game.equals("hub")) {
            player.performCommand("trigger hub");
            player.sendMessage("§a已触发返回大厅");
        } else {
            int x = minigameConfig.getInt("games." + game + ".x");
            int y = minigameConfig.getInt("games." + game + ".y");
            int z = minigameConfig.getInt("games." + game + ".z");
            String worldName = minigameConfig.getString("games." + game + ".world", "world");
            Location location = new Location(Bukkit.getWorld(worldName), x + 0.5, y, z + 0.5);
            if (location.getWorld() != null) {
                player.teleport(location);
                player.sendMessage("§a已传送至 " + minigameConfig.getString("games." + game + ".display", game));
            } else {
                player.sendMessage("§c世界 '" + worldName + "' 不存在！");
            }
        }
    }
    private void handlePlayerListClick(Player player, ItemStack item) {
        if (item.getType() == Material.PLAYER_HEAD) {
            Player target = Bukkit.getPlayer(ChatColor.stripColor(item.getItemMeta().getDisplayName()));
            if (target != null && !target.equals(player)) {
                spectatePlayer(player, target);
            } else {
                player.sendMessage("§c你不能观战自己！");
            }
        } else if (item.getType() == Material.ARROW) {
            int page = Integer.parseInt(ChatColor.stripColor(player.getOpenInventory().getTitle()).replace("在线玩家 - 页 ", "").trim()) - 1;
            openPlayerList(player, item.getItemMeta().getDisplayName().equals("§a上一页") ? page - 1 : page + 1);
        } else if (item.getType() == Material.BARRIER) {
            openMainDashboard(player);
        }
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Player target = spectating.get(player);
        if (target == null) return;
        if (!target.isOnline()) {
            exitSpectate(player);
            player.sendMessage("§c观战目标已离线，观战已结束");
            return;
        }
        if (player.getLocation().distance(target.getLocation()) > 10) {
            player.teleport(target.getLocation());
            player.sendMessage("§a你已超出观战目标10格范围，已传送回目标位置");
        }
    }
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (spectating.containsKey(player) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            exitSpectate(player);
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        resetPlayerState(player);
        chatCounts.putIfAbsent(player, 0);
        Bukkit.broadcastMessage("§7Lobby §8| §7" + player.getName() + " joined the game");
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (spectating.containsKey(player)) exitSpectate(player);
        spectating.entrySet().removeIf(entry -> entry.getValue().equals(player));
        saveChatCounts();
        chatCounts.remove(player);
        lastClaimTime.remove(player);
        Bukkit.broadcastMessage("§7Lobby §8| §7" + player.getName() + " left the game");
    }
    private void spectatePlayer(Player player, Player target) {
        resetPlayerState(player);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.teleport(target.getLocation());
        spectating.put(player, target);
        player.setMetadata("isSpectating", new FixedMetadataValue(plugin, true));
        player.sendMessage("§a正在观战 " + target.getName() + "，右键退出观战模式");
    }
    private void exitSpectate(Player player) {
        if (player.isOnline()) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.removeMetadata("isSpectating", plugin);
            player.teleport(player.getWorld().getSpawnLocation());
            player.sendMessage("§a已退出观战模式");
            Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);
        }
        spectating.remove(player);
    }
    private void resetPlayerState(Player player) {
        if (spectating.containsKey(player)) exitSpectate(player);
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }
        player.removeMetadata("isSpectating", plugin);
        if (player.getOpenInventory().getTitle().equals("§l服务大厅") ||
                player.getOpenInventory().getTitle().startsWith("§l在线玩家 - 页 ")) {
            player.closeInventory();
        }
        Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);
    }
    private void loadChatCounts() {
        YamlConfiguration config = configManager.getConfig("config.yml"); 
        dashboardChatLimit = config.getInt("dashboard_chat_limit", 5);
        if (dashboardChatLimit < 0) {
            dashboardChatLimit = 5;
            config.set("dashboard_chat_limit", 5);
            configManager.saveConfig("config.yml"); 
        }
        for (String playerName : config.getConfigurationSection("chat_counts") != null ?
                config.getConfigurationSection("chat_counts").getKeys(false) : new ArrayList<String>()) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                chatCounts.put(player, config.getInt("chat_counts." + playerName, 0));
            }
        }
    }
    private void saveChatCounts() {
        YamlConfiguration config = configManager.getConfig("config.yml"); 
        config.set("dashboard_chat_limit", dashboardChatLimit);
        chatCounts.forEach((player, count) -> config.set("chat_counts." + player.getName(), count));
        configManager.saveConfig("config.yml"); 
    }
    public void addDashboardChatLimit(int count) {
        synchronized (this) {
            dashboardChatLimit += Math.max(0, count);
            saveChatCounts();
            updateDashboard(Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getOpenInventory().getTitle().equals("§l服务大厅"))
                    .findFirst().orElse(null));
        }
    }
    public int getDashboardChatLimit() {
        return dashboardChatLimit;
    }
    public Map<Player, Integer> getChatCounts() {
        return chatCounts;
    }
    private void updateDashboard(Player player) {
        if (player != null && player.getOpenInventory().getTitle().equals("§l服务大厅")) {
            openMainDashboard(player);
        }
    }
}