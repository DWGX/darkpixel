package com.darkpixel.utils;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;
public class LogUtil {
    public static Logger logger;
    public static void init(JavaPlugin plugin) {
        logger = plugin.getLogger();
    }
    public static void info(String msg) {
        checkLogger();
        logger.info(msg);
    }
    public static void warning(String msg) {
        checkLogger();
        logger.warning(msg);
    }
    public static void severe(String msg) {
        checkLogger();
        logger.severe(msg);
    }
    private static void checkLogger() {
        if (logger == null) {
            throw new IllegalStateException("LogUtil 未初始化，请在插件启动时调用 LogUtil.init()");
        }
    }
}