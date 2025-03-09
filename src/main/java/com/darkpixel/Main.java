package com.darkpixel;

import com.darkpixel.utils.LogUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.TimeUnit;

public final class Main extends JavaPlugin {
    private Global context;

    @Override
    public void onEnable() {
        LogUtil.init(this);
        context = new Global(this);
        getLogger().info("DarkPixel 已启动");
    }

    @Override
    public void onDisable() {
        if (context != null) {
            context.shutdown();
        }
        try {
            if (!Global.executor.awaitTermination(15, TimeUnit.SECONDS)) {
                var pendingTasks = Global.executor.shutdownNow();
                getLogger().warning("线程池强制关闭，" + pendingTasks.size() + " 个任务未完成");
            }
        } catch (InterruptedException e) {
            Global.executor.shutdownNow();
            getLogger().severe("线程池关闭失败: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        getLogger().info("DarkPixel 已关闭");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reloadconfig")) {
            if (!sender.hasPermission("darkpixel.admin")) {
                sender.sendMessage("§c你没有权限执行此命令！");
                return true;
            }
            context.getConfigManager().reloadAllConfigs();
            context.updateEventRegistrations();
            sender.sendMessage("§a所有配置文件已重新加载！");
            return true;
        }
        return false;
    }

    public Global getContext() {
        return context;
    }
}