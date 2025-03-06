package com.darkpixel.utils;
import com.darkpixel.Global;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
public class WorldData {
    private final Map<String, WorldInfo> worldData = new HashMap<>();
    private final File file;
    private YamlConfiguration config;
    private final Global context;
    public WorldData(Global context) {
        this.context = context;
        file = new File(context.getPlugin().getDataFolder(), "world_data.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                LogUtil.severe("创建 world_data.yml 失败: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        loadData();
    }
    private void loadData() {
        for (String key : config.getKeys(false)) {
            int blockCount = config.getInt(key + ".blockCount", 0);
            worldData.put(key, new WorldInfo(blockCount));
        }
    }
    public void updateWorld(World world) {
        String name = world.getName();
        WorldInfo info = worldData.getOrDefault(name, new WorldInfo(0));
        info.blockCount = world.getLoadedChunks().length * 16 * 16 * world.getMaxHeight();
        worldData.put(name, info);
        saveDataAsync();
    }
    private void saveDataAsync() {
        worldData.forEach((name, info) -> config.set(name + ".blockCount", info.blockCount));
        FileUtil.saveAsync(config, file, context.getPlugin());
    }
    public String analyzeWorld(World world) {
        WorldInfo info = worldData.getOrDefault(world.getName(), new WorldInfo(0));
        return "世界 " + world.getName() + " 资源统计: " + info.blockCount + " 个方块已加载";
    }
    public static class WorldInfo {
        public int blockCount;
        public WorldInfo(int blockCount) {
            this.blockCount = blockCount;
        }
    }
}