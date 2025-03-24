package com.darkpixel.utils;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogUtil {
    public static Logger logger;

    public static void init(JavaPlugin plugin) {
        logger = plugin.getLogger();
        // 过滤特定日志
        logger.setFilter(record -> {
            String msg = record.getMessage();
            return !msg.contains("Checking for updates, please wait") &&
                    !msg.contains("NPC already exists at Location");
        });
    }

    public static void info(String message) {
        logger.info(message);
    }

    public static void warning(String message) {
        logger.warning(message);
    }

    public static void severe(String message) {
        logger.severe(message);
    }
    private static void checkLogger() {
        if (logger == null) {
            throw new IllegalStateException("LogUtil 未初始化，请在插件启动时调用 LogUtil.init()");
        }
    }
}