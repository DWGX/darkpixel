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
import java.util.*;
import java.util.stream.Collectors;

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
                .create();
    }

    @Override
    public void run() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api", new ApiHandler());
            server.setExecutor(null);
            server.start();
            Bukkit.getLogger().info("HTTP 服务器启动在端口 " + port);
            while (running && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            Bukkit.getLogger().severe("启动 HTTP 服务器失败: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stopServer();
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            Bukkit.getLogger().info("HTTP 服务器已关闭");
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
                    rankManager.refreshPlayerData(Bukkit.getPlayer(playerName));
                    PlayerData.PlayerInfo info = playerData.getPlayerInfo(playerName);
                    response.put("status", "success");
                    response.put("data", gson.toJson(info));
                } else if ("/api/set_particle".equals(path) && query != null) {
                    String playerName = getQueryParam(query, "player");
                    String particle = getQueryParam(query, "particle");
                    Player player = Bukkit.getPlayer(playerName);
                    if (player != null) {
                        playerData.setParticle(player, Particle.valueOf(particle));
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
                } else if ("/api/set_effects".equals(path) && query != null) {
                    String playerName = getQueryParam(query, "player");
                    String enabled = getQueryParam(query, "enabled");
                    PlayerData.PlayerInfo playerInfo = playerData.getPlayerInfo(playerName);
                    playerInfo.effectsEnabled = Boolean.parseBoolean(enabled);
                    playerData.saveData();
                    response.put("status", "success");
                } else if ("/api/online_players".equals(path)) {
                    response.put("status", "success");
                    response.put("players", Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                } else {
                    response.put("status", "error");
                    response.put("message", "未知命令或参数缺失");
                }
            } catch (Exception e) {
                response.put("status", "error");
                response.put("message", "处理请求失败: " + e.getMessage());
            }

            responseBody = gson.toJson(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
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
            out.name("effectsEnabled").value(value.effectsEnabled);
            out.endObject();
        }

        @Override
        public PlayerData.PlayerInfo read(JsonReader in) throws IOException {
            in.beginObject();
            String name = null;
            UUID uuid = null;
            List<String> groups = new ArrayList<>();
            boolean effectsEnabled = true;
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
                } else if ("effectsEnabled".equals(field)) {
                    effectsEnabled = in.nextBoolean();
                } else {
                    in.skipValue();
                }
            }
            in.endObject();
            PlayerData.PlayerInfo info = new PlayerData.PlayerInfo(name, uuid);
            info.groups = groups;
            info.effectsEnabled = effectsEnabled;
            return info;
        }
    }
}