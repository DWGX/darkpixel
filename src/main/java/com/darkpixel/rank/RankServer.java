package com.darkpixel.rank;

import com.darkpixel.utils.PlayerData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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
import java.util.*;

public class RankServer implements Runnable {
    private final RankManager rankManager;
    private final PlayerData playerData;
    private final Gson gson;
    private final int port;
    private volatile boolean running = true;
    private HttpServer server;

    public RankServer(RankManager rankManager, PlayerData playerData) {
        this.rankManager = rankManager;
        this.playerData = playerData;
        this.port = playerData.context.getConfigManager().getConfig().getInt("http_port", 25567);
        this.gson = new GsonBuilder()
                .registerTypeAdapter(PlayerData.PlayerInfo.class, new PlayerInfoTypeAdapter())
                .registerTypeAdapter(RankData.class, new RankDataTypeAdapter())
                .create();
    }

    @Override
    public void run() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api", new ApiHandler());
            server.setExecutor(null);
            server.start();
            Bukkit.getLogger().info("HTTP服务器跑在端口 " + port);
            while (running && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (IOException | InterruptedException e) {
            Bukkit.getLogger().severe("HTTP服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stopServer();
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            Bukkit.getLogger().info("HTTP服务器已关闭");
        }
    }

    public void shutdown() {
        running = false;
        stopServer();
    }

    class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            Map<String, Object> response = new HashMap<>();
            String responseBody;

            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            try {
                if ("/api/get_player_data".equals(path) && query != null) {
                    String playerName = getQueryParam(query, "player");
                    Player player = Bukkit.getPlayer(playerName);
                    PlayerData.PlayerInfo info = playerData.getPlayerInfo(playerName);
                    RankData rankData = rankManager.getAllRanks().getOrDefault(player != null ? player.getUniqueId() : Bukkit.getOfflinePlayer(playerName).getUniqueId(), new RankData("default", 0));
                    response.put("status", "success");
                    response.put("player_info", gson.toJson(info));
                    response.put("rank_data", gson.toJson(rankData));
                    response.put("online", player != null && player.isOnline());
                } else if ("/api/set_particle".equals(path) && query != null) {
                    String playerName = getQueryParam(query, "player");
                    String particle = getQueryParam(query, "particle");
                    Player player = Bukkit.getPlayer(playerName);
                    if (player != null) {
                        rankManager.setRank(player, rankManager.getRank(player), rankManager.getScore(player), Particle.valueOf(particle), rankManager.getJoinMessage(player));
                        response.put("status", "success");
                    } else {
                        response.put("status", "error");
                        response.put("message", "玩家不在线");
                    }
                } else if ("/api/set_join_message".equals(path) && query != null) {
                    String playerName = getQueryParam(query, "player");
                    String message = getQueryParam(query, "message");
                    Player player = Bukkit.getPlayer(playerName);
                    if (player != null) {
                        rankManager.setRank(player, rankManager.getRank(player), rankManager.getScore(player), rankManager.getJoinParticle(player), message);
                        response.put("status", "success");
                    } else {
                        response.put("status", "error");
                        response.put("message", "玩家不在线");
                    }
                } else if ("/api/set_group".equals(path) && query != null) {
                    String playerName = getQueryParam(query, "player");
                    String group = getQueryParam(query, "group");
                    Player player = Bukkit.getPlayer(playerName);
                    if (player != null) {
                        rankManager.setGroup(player, group);
                        response.put("status", "success");
                    } else {
                        response.put("status", "error");
                        response.put("message", "玩家不在线");
                    }
                } else if ("/api/set_rank".equals(path) && query != null) {
                    String playerName = getQueryParam(query, "player");
                    String rank = getQueryParam(query, "rank");
                    int score = Integer.parseInt(getQueryParam(query, "score"));
                    Player player = Bukkit.getPlayer(playerName);
                    UUID uuid = player != null ? player.getUniqueId() : Bukkit.getOfflinePlayer(playerName).getUniqueId();
                    rankManager.setRankByUUID(uuid, rank, score, rankManager.getJoinParticle(player), rankManager.getJoinMessage(player));
                    response.put("status", "success");
                } else if ("/api/list_players".equals(path)) {
                    response.put("status", "success");
                    List<Map<String, Object>> players = new ArrayList<>();
                    for (UUID uuid : rankManager.getAllRanks().keySet()) {
                        String name = Bukkit.getOfflinePlayer(uuid).getName();
                        RankData rankData = rankManager.getAllRanks().get(uuid);
                        Player player = Bukkit.getPlayer(uuid);
                        Map<String, Object> playerData = new HashMap<>();
                        playerData.put("name", name);
                        playerData.put("uuid", uuid.toString());
                        playerData.put("rank", rankData.getRank());
                        playerData.put("score", rankData.getScore());
                        playerData.put("join_particle", rankData.getJoinParticle().name());
                        playerData.put("join_message", rankData.getJoinMessage());
                        playerData.put("groups", rankManager.getPlayerGroups(player));
                        playerData.put("online", player != null && player.isOnline());
                        players.add(playerData);
                    }
                    response.put("players", players);
                } else if ("/api/list_groups".equals(path)) {
                    response.put("status", "success");
                    response.put("groups", rankManager.getGroups().keySet());
                } else if ("/api/create_group".equals(path) && query != null) {
                    String name = getQueryParam(query, "name");
                    String color = getQueryParam(query, "color");
                    String emoji = getQueryParam(query, "emoji");
                    String badge = getQueryParam(query, "badge");
                    String prefix = getQueryParam(query, "prefix");
                    rankManager.getGroups().put(name, new RankGroup(name, color, emoji, badge, prefix));
                    try (Connection conn = rankManager.getConnection();
                         PreparedStatement ps = conn.prepareStatement("INSERT INTO `groups` (name, color, emoji, badge, prefix) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, name);
                        ps.setString(2, color);
                        ps.setString(3, emoji);
                        ps.setString(4, badge);
                        ps.setString(5, prefix);
                        ps.executeUpdate();
                    }
                    response.put("status", "success");
                } else {
                    response.put("status", "error");
                    response.put("message", "啥命令啊？参数也不对");
                }
            } catch (Exception e) {
                response.put("status", "error");
                response.put("message", "出错了: " + e.getMessage());
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

        private String getQueryParam(String query, String key) {
            if (query == null) return null;
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2 && pair[0].equals(key)) {
                    return pair[1];
                }
            }
            return null;
        }
    }

    static class PlayerInfoTypeAdapter extends TypeAdapter<PlayerData.PlayerInfo> {
        @Override
        public void write(JsonWriter out, PlayerData.PlayerInfo value) throws IOException {
            out.beginObject();
            out.name("name").value(value.getName());
            out.name("uuid").value(value.getUuid().toString());
            out.name("groups").beginArray();
            for (String group : value.groups) {
                out.value(group);
            }
            out.endArray();
            out.endObject();
        }

        @Override
        public PlayerData.PlayerInfo read(JsonReader in) throws IOException {
            in.beginObject();
            String name = null;
            UUID uuid = null;
            List<String> groups = new ArrayList<>();
            while (in.hasNext()) {
                String field = in.nextName();
                if ("name".equals(field)) {
                    name = in.nextString();
                } else if ("uuid".equals(field)) {
                    uuid = UUID.fromString(in.nextString());
                } else if ("groups".equals(field)) {
                    in.beginArray();
                    while (in.hasNext()) {
                        groups.add(in.nextString());
                    }
                    in.endArray();
                } else {
                    in.skipValue();
                }
            }
            in.endObject();
            PlayerData.PlayerInfo info = new PlayerData.PlayerInfo(name, uuid);
            info.groups = groups;
            return info;
        }
    }

    static class RankDataTypeAdapter extends TypeAdapter<RankData> {
        @Override
        public void write(JsonWriter out, RankData value) throws IOException {
            out.beginObject();
            out.name("rank").value(value.getRank());
            out.name("score").value(value.getScore());
            out.name("join_particle").value(value.getJoinParticle().name());
            out.name("join_message").value(value.getJoinMessage());
            out.endObject();
        }

        @Override
        public RankData read(JsonReader in) throws IOException {
            in.beginObject();
            String rank = "default";
            int score = 0;
            Particle particle = Particle.FIREWORK;
            String joinMessage = "欢迎 {player} 加入服务器！";
            while (in.hasNext()) {
                String field = in.nextName();
                if ("rank".equals(field)) {
                    rank = in.nextString();
                } else if ("score".equals(field)) {
                    score = in.nextInt();
                } else if ("join_particle".equals(field)) {
                    particle = Particle.valueOf(in.nextString());
                } else if ("join_message".equals(field)) {
                    joinMessage = in.nextString();
                } else {
                    in.skipValue();
                }
            }
            in.endObject();
            RankData data = new RankData(rank, score);
            data.setJoinParticle(particle);
            data.setJoinMessage(joinMessage);
            return data;
        }
    }
}