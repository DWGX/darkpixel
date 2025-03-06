package com.darkpixel.ai;
import com.darkpixel.manager.ConfigManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
public class AiChatConfig {
    private final ConfigManager config;
    private final Set<String> whitelist = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Integer> playerMessageCount = new HashMap<>();
    private final Map<String, Integer> playerMessageLimits = new HashMap<>();
    private final int DEFAULT_MAX_MESSAGES;
    public AiChatConfig(ConfigManager config) {
        this.config = config;
        this.DEFAULT_MAX_MESSAGES = config.getConfig().getInt("default_max_messages", 5); 
    }
    public void loadConfig() {
        whitelist.addAll(config.getWhitelist());
        playerMessageLimits.putAll(config.getMessageLimits());
    }
    public void saveConfig() {
        config.saveWhitelist(whitelist);
        config.saveMessageLimits(playerMessageLimits);
    }
    public boolean canSendMessage(String player, boolean isOp) {
        return isOp || playerMessageCount.getOrDefault(player, 0) < getPlayerMessageLimit(player);
    }
    public void incrementMessageCount(String player) {
        playerMessageCount.merge(player, 1, Integer::sum);
    }
    public void resetMessageCount() {
        playerMessageCount.clear();
    }
    public void setPlayerMessageLimit(String player, int limit) {
        playerMessageLimits.put(player, Math.max(limit, DEFAULT_MAX_MESSAGES));
        saveConfig();
    }
    public int getPlayerMessageLimit(String player) {
        return playerMessageLimits.getOrDefault(player, DEFAULT_MAX_MESSAGES);
    }
    public Set<String> getWhitelist() {
        return whitelist;
    }
}