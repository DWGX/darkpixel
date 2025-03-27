package com.darkpixel.rank;

import com.darkpixel.Global;
import com.darkpixel.utils.PlayerData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RankServer implements Runnable {
    private final RankManager rankManager;
    private final PlayerData playerData;
    private final Global context;
    private final Gson gson;
    private final int port;
    private volatile boolean running = true;
    private HttpServer server;

    public RankServer(RankManager rankManager, PlayerData playerData, Global context) {
        this.rankManager = rankManager;
        this.playerData = playerData;
        this.context = context;
        this.port = context.getConfigManager().getConfig().getInt("http_port", 25567);
        this.gson = new GsonBuilder()
                .registerTypeAdapter(PlayerData.PlayerInfo.class, new PlayerInfoTypeAdapter())
                .registerTypeAdapter(RankData.class, new RankDataTypeAdapter())
                .create();
    }

    public void run() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api", new ApiHandler());
            server.setExecutor(null);
            server.start();
            while (running && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            stopServer();
        }
    }

    private void stopServer() {
        if (server != null) server.stop(0);
    }

    public void shutdown() {
        running = false;
        stopServer();
    }

    class ApiHandler implements com.sun.net.httpserver.HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            Map<String, Object> response = new HashMap<>();
            String responseBody;

            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            try {
                if ("/api/list_players".equals(path)) {
                    response.put("status", "success");
                    List<Map<String, Object>> players = new ArrayList<>();
                    List<Map.Entry<UUID, RankData>> rankedList = new ArrayList<>(rankManager.getAllRanks().entrySet());
                    rankedList.sort((a, b) -> Integer.compare(b.getValue().getScore(), a.getValue().getScore()));
                    int page = query != null ? Integer.parseInt(getQueryParam(query, "page", "1")) : 1;
                    int pageSize = query != null ? Integer.parseInt(getQueryParam(query, "pageSize", "10")) : 10;
                    int start = (page - 1) * pageSize;
                    int end = Math.min(start + pageSize, rankedList.size());
                    for (int i = start; i < end && i < rankedList.size(); i++) {
                        UUID uuid = rankedList.get(i).getKey();
                        RankData rankData = rankedList.get(i).getValue();
                        String name = Bukkit.getOfflinePlayer(uuid).getName();
                        if (name == null) continue;
                        Player player = Bukkit.getPlayer(uuid);
                        PlayerData.PlayerInfo info = playerData.getPlayerInfo(name);
                        Map<String, Object> playerData = new HashMap<>();
                        playerData.put("name", name);
                        playerData.put("uuid", uuid.toString());
                        playerData.put("rank", rankData.getRank());
                        playerData.put("score", rankData.getScore());
                        playerData.put("join_particle", rankData.getJoinParticle().name());
                        playerData.put("join_message", rankData.getJoinMessage());
                        playerData.put("groups", rankManager.getPlayerGroups(player));
                        playerData.put("chat_color", rankData.getChatColor());
                        playerData.put("show_rank", rankData.isShowRank());
                        playerData.put("show_vip", rankData.isShowVip());
                        playerData.put("show_group", rankData.isShowGroup());
                        playerData.put("ban_until", rankData.getBanUntil());
                        playerData.put("ban_reason", rankData.getBanReason());
                        playerData.put("online", player != null && player.isOnline());
                        playerData.put("login_count", info.loginCount);
                        playerData.put("last_sign_in", info.lastSignIn);
                        players.add(playerData);
                    }
                    response.put("players", players);
                    response.put("total", rankedList.size());
                    response.put("page", page);
                    response.put("pageSize", pageSize);
                } else if ("/api/list_groups".equals(path)) {
                    response.put("status", "success");
                    List<Map<String, String>> groups = new ArrayList<>();
                    for (RankGroup group : rankManager.getGroups().values()) {
                        Map<String, String> groupData = new HashMap<>();
                        groupData.put("name", group.getName());
                        groupData.put("color", group.getColor());
                        groupData.put("emoji", group.getEmoji());
                        groupData.put("badge", group.getBadge());
                        groupData.put("prefix", group.getPrefix());
                        groups.add(groupData);
                    }
                    response.put("groups", groups);
                } else if ("/api/set_score".equals(path) && query != null) {
                    UUID uuid = UUID.fromString(getQueryParam(query, "player"));
                    int score = Integer.parseInt(getQueryParam(query, "score"));
                    rankManager.setScoreByUUID(uuid, score);
                    response.put("status", "success");
                } else if ("/api/set_rank".equals(path) && query != null) {
                    UUID uuid = UUID.fromString(getQueryParam(query, "player"));
                    String rank = getQueryParam(query, "rank");
                    RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
                    rankManager.setRankByUUID(uuid, rank, data.getScore(), data.getJoinParticle(), data.getJoinMessage());
                    response.put("status", "success");
                } else if ("/api/set_group".equals(path) && query != null) {
                    UUID uuid = UUID.fromString(getQueryParam(query, "player"));
                    String group = getQueryParam(query, "group");
                    rankManager.setGroupByUUID(uuid, group);
                    PlayerData.PlayerInfo info = playerData.getPlayerInfo(Bukkit.getOfflinePlayer(uuid).getName());
                    info.groups.clear();
                    info.groups.add(group);
                    playerData.saveData();
                    response.put("status", "success");
                } else if ("/api/set_particle".equals(path) && query != null) {
                    UUID uuid = UUID.fromString(getQueryParam(query, "player"));
                    String particle = getQueryParam(query, "particle");
                    RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
                    rankManager.setRankByUUID(uuid, data.getRank(), data.getScore(), Particle.valueOf(particle), data.getJoinMessage());
                    response.put("status", "success");
                } else if ("/api/set_join_message".equals(path) && query != null) {
                    UUID uuid = UUID.fromString(getQueryParam(query, "player"));
                    String message = getQueryParam(query, "message");
                    RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
                    rankManager.setRankByUUID(uuid, data.getRank(), data.getScore(), data.getJoinParticle(), message);
                    response.put("status", "success");
                } else if ("/api/set_chat_color".equals(path) && query != null) {
                    UUID uuid = UUID.fromString(getQueryParam(query, "player"));
                    String chatColor = getQueryParam(query, "chat_color");
                    RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
                    data.setChatColor(chatColor);
                    rankManager.setRankByUUID(uuid, data.getRank(), data.getScore(), data.getJoinParticle(), data.getJoinMessage());
                    response.put("status", "success");
                } else if ("/api/set_display_options".equals(path) && query != null) {
                    UUID uuid = UUID.fromString(getQueryParam(query, "player"));
                    boolean showRank = Boolean.parseBoolean(getQueryParam(query, "show_rank"));
                    boolean showVip = Boolean.parseBoolean(getQueryParam(query, "show_vip"));
                    boolean showGroup = Boolean.parseBoolean(getQueryParam(query, "show_group"));
                    RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
                    data.setShowRank(showRank);
                    data.setShowVip(showVip);
                    data.setShowGroup(showGroup);
                    rankManager.setRankByUUID(uuid, data.getRank(), data.getScore(), data.getJoinParticle(), data.getJoinMessage());
                    response.put("status", "success");
                } else if ("/api/ban".equals(path) && query != null) {
                    UUID uuid = UUID.fromString(getQueryParam(query, "player"));
                    long banTime = Long.parseLong(getQueryParam(query, "banTime", "0"));
                    String reason = getQueryParam(query, "reason", "未指定原因");
                    long banUntil = banTime == -1 ? -1 : (banTime > 0 ? System.currentTimeMillis() + banTime * 60000 : 0);
                    context.getBanManager().banPlayer(Bukkit.getOfflinePlayer(uuid).getName(), banUntil, reason);
                    PlayerData.PlayerInfo info = playerData.getPlayerInfo(Bukkit.getOfflinePlayer(uuid).getName());
                    info.groups.clear();
                    info.groups.add("banned");
                    playerData.saveData();
                    RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
                    data.setBanUntil(banUntil);
                    data.setBanReason(reason);
                    response.put("status", "success");
                } else if ("/api/unban".equals(path) && query != null) {
                    UUID uuid = UUID.fromString(getQueryParam(query, "player"));
                    context.getBanManager().unbanPlayer(Bukkit.getOfflinePlayer(uuid).getName());
                    PlayerData.PlayerInfo info = playerData.getPlayerInfo(Bukkit.getOfflinePlayer(uuid).getName());
                    info.groups.remove("banned");
                    if (info.groups.isEmpty()) info.groups.add("member");
                    playerData.saveData();
                    RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
                    data.setBanUntil(0);
                    data.setBanReason(null);
                    response.put("status", "success");
                } else if ("/api/create_group".equals(path) && query != null) {
                    String name = getQueryParam(query, "name");
                    String color = getQueryParam(query, "color", "§f");
                    String emoji = getQueryParam(query, "emoji", "");
                    String badge = getQueryParam(query, "badge", "");
                    String prefix = getQueryParam(query, "prefix", "[" + name + "]");
                    rankManager.getGroups().put(name, new RankGroup(name, color, emoji, badge, prefix));
                    try (Connection conn = rankManager.getConnection();
                         PreparedStatement ps = conn.prepareStatement("INSERT INTO `groups` (name, color, emoji, badge, prefix) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE color = ?, emoji = ?, badge = ?, prefix = ?")) {
                        ps.setString(1, name);
                        ps.setString(2, color);
                        ps.setString(3, emoji);
                        ps.setString(4, badge);
                        ps.setString(5, prefix);
                        ps.setString(6, color);
                        ps.setString(7, emoji);
                        ps.setString(8, badge);
                        ps.setString(9, prefix);
                        ps.executeUpdate();
                    }
                    response.put("status", "success");
                } else if ("/api/delete_group".equals(path) && query != null) {
                    String name = getQueryParam(query, "name");
                    rankManager.getGroups().remove(name);
                    try (Connection conn = rankManager.getConnection();
                         PreparedStatement ps = conn.prepareStatement("DELETE FROM `groups` WHERE name = ?")) {
                        ps.setString(1, name);
                        ps.executeUpdate();
                    }
                    response.put("status", "success");
                } else {
                    response.put("status", "error");
                    response.put("message", "无效请求: " + path);
                }
            } catch (Exception e) {
                response.put("status", "error");
                response.put("message", "错误: " + e.getMessage());
                e.printStackTrace();
            }

            responseBody = gson.toJson(response);
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
                os.flush();
            }
        }

        private String getQueryParam(String query, String key, String defaultValue) {
            if (query == null) return defaultValue;
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2 && pair[0].equals(key)) return pair[1];
            }
            return defaultValue;
        }

        private String getQueryParam(String query, String key) {
            return getQueryParam(query, key, null);
        }
    }

    class RankDataTypeAdapter extends TypeAdapter<RankData> {
        public void write(JsonWriter out, RankData value) throws IOException {
            out.beginObject();
            out.name("rank").value(value.getRank());
            out.name("score").value(value.getScore());
            out.name("join_particle").value(value.getJoinParticle().name());
            out.name("join_message").value(value.getJoinMessage());
            out.name("groups").value(String.join(",", value.getGroups()));
            out.name("chat_color").value(value.getChatColor());
            out.name("show_rank").value(value.isShowRank());
            out.name("show_vip").value(value.isShowVip());
            out.name("show_group").value(value.isShowGroup());
            out.name("ban_until").value(value.getBanUntil());
            out.name("ban_reason").value(value.getBanReason());
            out.endObject();
        }

        public RankData read(JsonReader in) throws IOException {
            RankData data = new RankData("member", 0);
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "rank": data.setRank(in.nextString()); break;
                    case "score": data.setScore(in.nextInt()); break;
                    case "join_particle": data.setJoinParticle(Particle.valueOf(in.nextString())); break;
                    case "join_message": data.setJoinMessage(in.nextString()); break;
                    case "chat_color": data.setChatColor(in.nextString()); break;
                    case "show_rank": data.setShowRank(in.nextBoolean()); break;
                    case "show_vip": data.setShowVip(in.nextBoolean()); break;
                    case "show_group": data.setShowGroup(in.nextBoolean()); break;
                    case "ban_until": data.setBanUntil(in.nextLong()); break;
                    case "ban_reason": data.setBanReason(in.nextString()); break;
                    default: in.skipValue();
                }
            }
            in.endObject();
            return data;
        }
    }

    class PlayerInfoTypeAdapter extends TypeAdapter<PlayerData.PlayerInfo> {
        public void write(JsonWriter out, PlayerData.PlayerInfo value) throws IOException {
            out.beginObject();
            out.name("login_count").value(value.loginCount);
            out.name("last_sign_in").value(value.lastSignIn);
            out.endObject();
        }

        public PlayerData.PlayerInfo read(JsonReader in) throws IOException {
            int loginCount = 0;
            long lastSignIn = 0;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "login_count": loginCount = in.nextInt(); break;
                    case "last_sign_in": lastSignIn = in.nextLong(); break;
                    default: in.skipValue();
                }
            }
            in.endObject();
            PlayerData.PlayerInfo info = new PlayerData.PlayerInfo(loginCount, Bukkit.getWorld("world").getSpawnLocation());
            info.lastSignIn = lastSignIn;
            return info;
        }
    }
}