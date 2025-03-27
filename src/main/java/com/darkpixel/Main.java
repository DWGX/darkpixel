package com.darkpixel;

import com.darkpixel.rank.RankServer;
import com.darkpixel.utils.LogUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public final class Main extends JavaPlugin {
    private Global context;
    private Thread rankServerThread;

    @Override
    public void onEnable() {
        try {
            LogUtil.init(this);
            getLogger().info("正在初始化 LogUtil...");
            context = new Global(this);
            getLogger().info("Global 初始化完成");

            rankServerThread = new Thread(context.getRankServer(), "RankServer-Thread");
            rankServerThread.start();
            getLogger().info("RankServer 线程已启动");

            getLogger().info("DarkPixel v1.0 已成功启用");
        } catch (Exception e) {
            getLogger().severe("DarkPixel 初始化失败: " + e.getMessage());
            e.printStackTrace();
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        try {
            // 保存 RankManager 数据
            if (context != null && context.getRankManager() != null) {
                context.getRankManager().saveAll();
                getLogger().info("RankManager 数据已保存");
            } else {
                getLogger().warning("RankManager 未初始化，跳过保存");
            }

            // 关闭 RankServer
            if (context != null && context.getRankServer() != null) {
                context.getRankServer().shutdown();
                try {
                    rankServerThread.join(5000); // 等待线程优雅关闭
                    if (rankServerThread.isAlive()) {
                        getLogger().warning("RankServer 线程未能及时关闭，可能存在资源泄漏");
                    } else {
                        getLogger().info("RankServer 线程已关闭");
                    }
                } catch (InterruptedException e) {
                    getLogger().severe("等待 RankServer 线程关闭时被中断: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            } else {
                getLogger().warning("RankServer 未初始化，跳过关闭");
            }

            // 清理 Global 资源
            if (context != null) {
                context.shutdown();
                getLogger().info("Global 资源已清理");
            }

            getLogger().info("DarkPixel v1.0 已成功禁用");
        } catch (Exception e) {
            getLogger().severe("DarkPixel 禁用过程中出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reloadconfig")) {
            if (!sender.hasPermission("darkpixel.admin")) {
                sender.sendMessage("§c你没有权限执行此命令！");
                return true;
            }
            if (context == null) {
                sender.sendMessage("§c插件未正确初始化，无法重新加载配置！");
                return true;
            }
            Global.executor.submit(() -> {
                try {
                    context.getConfigManager().reloadAllConfigsAsync();
                    context.getRankManager().reload();
                    sender.sendMessage("§a所有配置文件已重新加载！");
                } catch (Exception e) {
                    sender.sendMessage("§c重新加载配置失败: " + e.getMessage());
                    getLogger().severe("重新加载配置失败: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            return true;
        }
        return false;
    }

    public Global getContext() {
        return context;
    }
}