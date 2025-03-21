package com.darkpixel.rank;

import com.darkpixel.Global;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class RankManager {
    private final Global context;
    private final File rankFile;
    private final File groupFile;
    private final Gson gson;
    private Map<UUID, PlayerRank> ranks;
    private Map<String, RankGroup> groups;
    private Map<UUID, List<String>> playerGroups;

    public RankManager(Global context) {
        this.context = context;
        this.rankFile = new File("D:/Project/darkpixel/data", "ranks.json");
        this.groupFile = new File("D:/Project/darkpixel/data", "groups.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadRanks();
        loadGroups();
        context.getPlugin().getServer().getPluginManager().registerEvents(new RankEvents(this), context.getPlugin());
    }

    private void loadRanks() {
        if (!rankFile.exists()) {
            rankFile.getParentFile().mkdirs();
            ranks = new HashMap<>();
            saveRanks();
            return;
        }
        try (FileReader reader = new FileReader(rankFile)) {
            Type type = new TypeToken<Map<UUID, PlayerRank>>() {}.getType();
            ranks = gson.fromJson(reader, type);
            if (ranks == null) ranks = new HashMap<>();
        } catch (IOException e) {
            ranks = new HashMap<>();
        }
    }

    private void loadGroups() {
        if (!groupFile.exists()) {
            groupFile.getParentFile().mkdirs();
            groups = new HashMap<>();
            playerGroups = new HashMap<>();
            saveGroups();
            return;
        }
        try (FileReader reader = new FileReader(groupFile)) {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> data = gson.fromJson(reader, type);
            if (data == null) {
                groups = new HashMap<>();
                playerGroups = new HashMap<>();
            } else {
                groups = (Map<String, RankGroup>) data.get("groups");
                playerGroups = (Map<UUID, List<String>>) data.get("playerGroups");
                if (groups == null) groups = new HashMap<>();
                if (playerGroups == null) playerGroups = new HashMap<>();
            }
        } catch (IOException e) {
            groups = new HashMap<>();
            playerGroups = new HashMap<>();
        }
    }

    public synchronized void saveRanks() {
        try (FileWriter writer = new FileWriter(rankFile)) {
            gson.toJson(ranks, writer);
        } catch (IOException e) {}
    }

    public synchronized void saveGroups() {
        try (FileWriter writer = new FileWriter(groupFile)) {
            Map<String, Object> data = new HashMap<>();
            data.put("groups", groups);
            data.put("playerGroups", playerGroups);
            gson.toJson(data, writer);
        } catch (IOException e) {}
    }

    public void setRank(Player player, String rank, int score) {
        UUID uuid = player.getUniqueId();
        PlayerRank playerRank = ranks.getOrDefault(uuid, new PlayerRank(player.getName()));
        playerRank.setRank(rank);
        playerRank.setScore(score);
        playerRank.setLastModified(System.currentTimeMillis());
        ranks.put(uuid, playerRank);
        saveRanks();
    }

    public String getRank(Player player) {
        PlayerRank playerRank = ranks.get(player.getUniqueId());
        return playerRank != null ? playerRank.getRank() : "member";
    }

    public int getScore(Player player) {
        PlayerRank playerRank = ranks.get(player.getUniqueId());
        return playerRank != null ? playerRank.getScore() : 0;
    }

    public boolean isEffectsEnabled(Player player) {
        PlayerRank playerRank = ranks.get(player.getUniqueId());
        return playerRank != null ? playerRank.isEnableEffects() : true;
    }

    public void toggleEffects(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerRank playerRank = ranks.getOrDefault(uuid, new PlayerRank(player.getName()));
        playerRank.setEnableEffects(!playerRank.isEnableEffects());
        ranks.put(uuid, playerRank);
        saveRanks();
    }

    public Map<UUID, PlayerRank> getAllRanks() {
        return new HashMap<>(ranks);
    }

    public void addGroup(String groupName) {
        if (!groups.containsKey(groupName)) {
            groups.put(groupName, new RankGroup(groupName, "", "", ""));
            saveGroups();
        }
    }

    public void updateGroup(String groupName, String color, String emoji, String badge) {
        RankGroup group = groups.get(groupName);
        if (group != null) {
            group.setColor(color);
            group.setEmoji(emoji);
            group.setBadge(badge);
            saveGroups();
        }
    }

    public void removeGroup(String groupName) {
        groups.remove(groupName);
        for (List<String> playerGroupList : playerGroups.values()) {
            playerGroupList.remove(groupName);
        }
        saveGroups();
    }

    public void addPlayerToGroup(Player player, String groupName) {
        if (!groups.containsKey(groupName)) return;
        UUID uuid = player.getUniqueId();
        playerGroups.computeIfAbsent(uuid, k -> new ArrayList<>()).add(groupName);
        saveGroups();
    }

    public void removePlayerFromGroup(Player player, String groupName) {
        UUID uuid = player.getUniqueId();
        List<String> playerGroupList = playerGroups.get(uuid);
        if (playerGroupList != null) {
            playerGroupList.remove(groupName);
            saveGroups();
        }
    }

    public List<String> getPlayerGroups(Player player) {
        return playerGroups.getOrDefault(player.getUniqueId(), Collections.emptyList());
    }

    public Map<String, RankGroup> getGroups() {
        return groups;
    }
}