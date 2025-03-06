package com.darkpixel.utils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
public class FileUtil {
    public static YamlConfiguration loadOrCreate(File file, JavaPlugin plugin, String resourceName) {
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
    public static synchronized void save(YamlConfiguration config, File file, JavaPlugin plugin) {
        try {
            config.save(file);
        } catch (IOException e) {
            LogUtil.severe("保存文件 " + file.getName() + " 失败: " + e.getMessage());
        }
    }
    public static CompletableFuture<Void> saveAsync(YamlConfiguration config, File file, JavaPlugin plugin) {
        return CompletableFuture.runAsync(() -> save(config, file, plugin));
    }
}