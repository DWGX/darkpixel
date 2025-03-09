package com.darkpixel.ai;

import com.darkpixel.utils.LogUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class AiChatEvents implements Listener {
    private final AiChatHandler handler;
    private final AiChatHistory history;

    public AiChatEvents(AiChatHandler handler) {
        this.handler = handler;
        if (handler instanceof AiChatHandlerImpl) {
            this.history = ((AiChatHandlerImpl) handler).chatHistory;
        } else {
            throw new IllegalArgumentException("AiChatHandler must be an instance of AiChatHandlerImpl");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        String name = e.getPlayer().getName();
        LogUtil.info(name + "加入服务器");
        history.addMessage(name, "");
        history.stoppedPlayers.remove(name);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        LogUtil.info(e.getPlayer().getName() + "离开服务器");
        history.saveChatHistoryAsync(); // 使用异步保存
    }
}