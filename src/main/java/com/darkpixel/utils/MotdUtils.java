package com.darkpixel.utils;
import com.darkpixel.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.CachedServerIcon;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
public class MotdUtils implements Listener {
    private final Main plugin;
    private final List<String> motdFrames;
    private final List<CachedServerIcon> serverIcons;
    private int currentFrame = 0;
    private static final ChatColor[] RAINBOW_COLORS = {
            ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW, ChatColor.GREEN,
            ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE
    };
    public MotdUtils(Main plugin) {
        this.plugin = plugin;
        this.motdFrames = new ArrayList<>();
        this.serverIcons = new ArrayList<>();
        motdFrames.add(rainbowText("DarkPixel") + "\n" + ChatColor.GRAY + "这里是黑像素BlackPixel!");
        motdFrames.add(gradientText("DarkPixel", ChatColor.RED, ChatColor.YELLOW) + "\n" + ChatColor.GREEN + "你知道吗“DarkPixel不怎么有黑客");
        motdFrames.add(rainbowText("DarkPixel") + "\n" + ChatColor.YELLOW + "这里有子服务器 多种小游戏");
        motdFrames.add(gradientText("DarkPixel", ChatColor.AQUA, ChatColor.BLUE) + "\n" + ChatColor.AQUA + "快来加入我们吧~^^");
        motdFrames.add(waveText("DarkPixel", ChatColor.RED) + "\n" + ChatColor.RED + "PVE等你挖掘");
        motdFrames.add(gradientText("DarkPixel", ChatColor.BLUE, ChatColor.DARK_AQUA) + "\n" + ChatColor.BLUE + "空岛战争 Skyblock");
        motdFrames.add(rainbowText("DarkPixel") + "\n" + ChatColor.LIGHT_PURPLE + "狼人杀 智商对决!");
        motdFrames.add(fadeText("DarkPixel", ChatColor.GOLD) + "\n" + ChatColor.GOLD + "击退战争 怀疑人生?");
        motdFrames.add(gradientText("DarkPixel", ChatColor.DARK_GREEN, ChatColor.GREEN) + "\n" + ChatColor.DARK_GREEN + "密室杀手 悬念丛生");
        motdFrames.add(rainbowText("DarkPixel") + "\n" + ChatColor.DARK_AQUA + "起床战争 床在人在!");
        loadServerIcons();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startFrameSwitchTask();
    }
    private void loadServerIcons() {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files != null) {
            plugin.getLogger().info("Found " + files.length + " PNG files.");
            for (File file : files) {
                try {
                    CachedServerIcon icon = Bukkit.loadServerIcon(file);
                    serverIcons.add(icon);
                    plugin.getLogger().info("Loaded icon: " + file.getName());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load server icon: " + file.getName());
                }
            }
        } else {
            plugin.getLogger().warning("No PNG files found in " + folder.getPath());
        }
    }
    public static String rainbowText(String text) {
        StringBuilder rainbow = new StringBuilder();
        int colorIndex = 0;
        for (char c : text.toCharArray()) {
            rainbow.append(RAINBOW_COLORS[colorIndex]).append(c);
            colorIndex = (colorIndex + 1) % RAINBOW_COLORS.length;
        }
        return rainbow.toString();
    }
    public static String gradientText(String text, ChatColor startColor, ChatColor endColor) {
        StringBuilder gradient = new StringBuilder();
        ChatColor[] colors = {startColor, ChatColor.YELLOW, endColor};
        int segmentLength = text.length() / (colors.length - 1);
        int colorIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            if (i > 0 && i % segmentLength == 0 && colorIndex < colors.length - 1) {
                colorIndex++;
            }
            gradient.append(colors[colorIndex]).append(text.charAt(i));
        }
        return gradient.toString();
    }
    public static String waveText(String text, ChatColor baseColor) {
        StringBuilder wave = new StringBuilder();
        boolean alternate = false;
        for (char c : text.toCharArray()) {
            wave.append(alternate ? ChatColor.WHITE : baseColor).append(c);
            alternate = !alternate;
        }
        return wave.toString();
    }
    public static String fadeText(String text, ChatColor baseColor) {
        StringBuilder fade = new StringBuilder();
        ChatColor[] fadeColors = {ChatColor.GRAY, baseColor, ChatColor.WHITE, baseColor, ChatColor.GRAY};
        int colorIndex = 0;
        for (char c : text.toCharArray()) {
            fade.append(fadeColors[colorIndex]).append(c);
            colorIndex = (colorIndex + 1) % fadeColors.length;
        }
        return fade.toString();
    }
    public static String randomColorText(String text) {
        StringBuilder random = new StringBuilder();
        for (char c : text.toCharArray()) {
            ChatColor color = RAINBOW_COLORS[(int) (Math.random() * RAINBOW_COLORS.length)];
            random.append(color).append(c);
        }
        return random.toString();
    }
    private void startFrameSwitchTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!motdFrames.isEmpty()) {
                    currentFrame = (currentFrame + 1) % motdFrames.size();
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }
    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (!motdFrames.isEmpty()) {
            event.setMotd(motdFrames.get(currentFrame));
        }
        if (!serverIcons.isEmpty()) {
            event.setServerIcon(serverIcons.get(currentFrame % serverIcons.size()));
        }
        event.setMaxPlayers(100);
    }
    public String getCurrentMotd() {
        return motdFrames.isEmpty() ? "No MOTD set" : motdFrames.get(currentFrame);
    }
    public void addMotdFrame(String frame) {
        motdFrames.add(frame);
    }
}