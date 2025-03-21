package com.darkpixel.rank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RankWebServer {
    private static final File RANK_FILE = new File("D:/Project/darkpixel/data", "ranks.json");
    private static final File GROUP_FILE = new File("D:/Project/darkpixel/data", "groups.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/ranks", new RankHandler());
        server.createContext("/groups", new GroupHandler());
        // server.createContext("/online-players", new OnlinePlayersHandler()); // 注释掉
        server.createContext("/toggle-effects", new ToggleEffectsHandler());
        server.createContext("/groups/create", new GroupCreateHandler());
        server.createContext("/groups/edit", new GroupEditHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("RankWebServer started on port 8080");
    }

    static class RankHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<UUID, PlayerRank> ranks = loadRanks();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerRank pr = ranks.getOrDefault(player.getUniqueId(), new PlayerRank(player.getName()));
                    pr.setName(player.getName());
                    ranks.put(player.getUniqueId(), pr);
                }
                String json = GSON.toJson(ranks);
                sendResponse(exchange, 200, json);
            }
        }
    }

    static class GroupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                Map<String, Object> data = loadGroups();
                String json = GSON.toJson(data);
                sendResponse(exchange, 200, json);
            } else if ("POST".equalsIgnoreCase(method)) {
                InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                Map<String, Object> data = GSON.fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
                reader.close();
                saveGroups(data);
                sendResponse(exchange, 200, "Groups updated successfully");
            }
        }
    }

    static class OnlinePlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<UUID, PlayerRank> ranks = loadRanks();
                List<Map<String, Object>> onlinePlayers = Bukkit.getOnlinePlayers().stream().map(player -> {
                    Map<String, Object> playerData = new HashMap<>();
                    PlayerRank pr = ranks.getOrDefault(player.getUniqueId(), new PlayerRank(player.getName()));
                    playerData.put("uuid", player.getUniqueId().toString());
                    playerData.put("name", player.getName());
                    playerData.put("rank", pr.getRank());
                    playerData.put("score", pr.getScore());
                    playerData.put("avatar", "https://minotar.net/avatar/" + player.getName() + "/32.png");
                    return playerData;
                }).toList();
                String json = GSON.toJson(onlinePlayers);
                sendResponse(exchange, 200, json);
            }
        }
    }

    static class ToggleEffectsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                Map<String, String> data = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
                reader.close();
                UUID uuid = UUID.fromString(data.get("uuid"));
                Map<UUID, PlayerRank> ranks = loadRanks();
                PlayerRank pr = ranks.get(uuid);
                if (pr != null) {
                    pr.setEnableEffects(!pr.isEnableEffects());
                    ranks.put(uuid, pr);
                    saveRanks(ranks);
                }
                sendResponse(exchange, 200, "Effects toggled successfully");
            }
        }
    }

    static class GroupCreateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                Map<String, String> data = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
                reader.close();
                String groupName = data.get("groupName");
                Map<String, Object> groupsData = loadGroups();
                Map<String, RankGroup> groups = (Map<String, RankGroup>) groupsData.get("groups");
                if (!groups.containsKey(groupName)) {
                    groups.put(groupName, new RankGroup(groupName, "", "", ""));
                    saveGroups(groupsData);
                    sendResponse(exchange, 200, "Group created successfully");
                } else {
                    sendResponse(exchange, 400, "Group already exists");
                }
            }
        }
    }

    static class GroupEditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                Map<String, String> data = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
                reader.close();
                String groupName = data.get("groupName");
                String color = data.get("color");
                String emoji = data.get("emoji");
                String badge = data.get("badge");
                Map<String, Object> groupsData = loadGroups();
                Map<String, RankGroup> groups = (Map<String, RankGroup>) groupsData.get("groups");
                RankGroup group = groups.get(groupName);
                if (group != null) {
                    group.setColor(color != null ? color : "");
                    group.setEmoji(emoji != null ? emoji : "");
                    group.setBadge(badge != null ? badge : "");
                    saveGroups(groupsData);
                    sendResponse(exchange, 200, "Group updated successfully");
                } else {
                    sendResponse(exchange, 404, "Group not found");
                }
            }
        }
    }

    private static Map<UUID, PlayerRank> loadRanks() {
        if (!RANK_FILE.exists()) return new HashMap<>();
        try (FileReader reader = new FileReader(RANK_FILE)) {
            return GSON.fromJson(reader, new TypeToken<Map<UUID, PlayerRank>>() {}.getType());
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private static void saveRanks(Map<UUID, PlayerRank> ranks) {
        try (FileWriter writer = new FileWriter(RANK_FILE)) {
            GSON.toJson(ranks, writer);
        } catch (IOException e) {}
    }

    private static Map<String, Object> loadGroups() {
        if (!GROUP_FILE.exists()) return new HashMap<>();
        try (FileReader reader = new FileReader(GROUP_FILE)) {
            return GSON.fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private static void saveGroups(Map<String, Object> data) {
        try (FileWriter writer = new FileWriter(GROUP_FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {}
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}