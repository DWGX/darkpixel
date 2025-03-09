package com.darkpixel.ai;

import com.darkpixel.manager.ConfigManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface AiChatHandler {
    void init(ConfigManager config);
    String getPlayerModel(String player);
    void setPlayerModel(String player, String model);
    boolean canSendMessage(String player, boolean isOp);
    CompletableFuture<String> sendMessage(Player player, String message, boolean isPublic);
    void sendMessage(Player player, String message, boolean isPublic, Consumer<String> callback);
    void sendAdminBroadcast(Player player, String message);
    void setPlayerMessageLimit(String player, int limit);
    int getPlayerMessageLimit(String player);
    void handleAdminCommand(Player player, String subCommand, String message);
    void shutdown(); // 新增 shutdown 方法
}