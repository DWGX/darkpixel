package com.darkpixel.rank;

import com.darkpixel.Global;
import com.darkpixel.utils.PlayerData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RankServer implements Runnable {
    private final RankManager rankManager;
    private final PlayerData playerData;
    private final Global context;
    private final Gson gson;
    private final int port;
    private volatile boolean running = true;
    private HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public RankServer(RankManager rankManager, PlayerData playerData, Global context) {
        this.rankManager = rankManager;
        this.playerData = playerData;
        this.context = context;
        this.port = context.getConfigManager().getConfig().getInt("http_port", 25560);
        this.gson = new GsonBuilder().create();
    }

    public void run() {
        if (!context.isRankServerRunning()) return;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api", new ApiHandler());
            server.setExecutor(executor);
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
        executor.shutdown();
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
                    handleListPlayers(query, response);
                } else if ("/api/list_groups".equals(path)) {
                    handleListGroups(response);
                } else if ("/api/set_score".equals(path) && query != null) {
                    handleSetScore(query, response);
                } else if ("/api/set_rank".equals(path) && query != null) {
                    handleSetRank(query, response);
                } else if ("/api/set_group".equals(path) && query != null) {
                    handleSetGroup(query, response);
                } else if ("/api/set_particle".equals(path) && query != null) {
                    handleSetParticle(query, response);
                } else if ("/api/set_join_message".equals(path) && query != null) {
                    handleSetJoinMessage(query, response);
                } else if ("/api/set_chat_color".equals(path) && query != null) {
                    handleSetChatColor(query, response);
                } else if ("/api/set_display_options".equals(path) && query != null) {
                    handleSetDisplayOptions(query, response);
                } else if ("/api/ban".equals(path) && query != null) {
                    handleBan(query, response);
                } else if ("/api/unban".equals(path) && query != null) {
                    handleUnban(query, response);
                } else if ("/api/create_group".equals(path) && query != null) {
                    handleCreateGroup(query, response);
                } else if ("/api/delete_group".equals(path) && query != null) {
                    handleDeleteGroup(query, response);
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

        private void handleListPlayers(String query, Map<String, Object> response) {
            int page = query != null ? Integer.parseInt(getQueryParam(query, "page", "1")) : 1;
            int pageSize = query != null ? Integer.parseInt(getQueryParam(query, "pageSize", "100")) : 100;
            List<Map<String, Object>> players = new ArrayList<>();
            List<Map.Entry<UUID, RankData>> rankedList = new ArrayList<>(rankManager.getAllRanks().entrySet());
            rankedList.sort((a, b) -> Integer.compare(b.getValue().getScore(), a.getValue().getScore()));
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, rankedList.size());
            for (int i = start; i < end && i < rankedList.size(); i++) {
                UUID uuid = rankedList.get(i).getKey();
                RankData rankData = rankedList.get(i).getValue();
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) continue; // 跳过无名玩家
                Player player = Bukkit.getPlayer(uuid);
                PlayerData.PlayerInfo info = playerData.getPlayerInfo(name);
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("name", name);
                playerData.put("uuid", uuid.toString());
                playerData.put("rank", rankData.getRank());
                playerData.put("score", rankData.getScore());
                playerData.put("join_particle", rankData.getJoinParticle().name());
                playerData.put("join_message", rankData.getJoinMessage());
                playerData.put("groups", rankManager.getPlayerGroups(uuid));
                playerData.put("chat_color", rankData.getChatColor());
                playerData.put("show_rank", rankData.isShowRank());
                playerData.put("show_vip", rankData.isShowVip());
                playerData.put("show_group", rankData.isShowGroup());
                playerData.put("show_score", rankData.isShowScore());
                playerData.put("ban_until", rankData.getBanUntil());
                playerData.put("ban_reason", rankData.getBanReason());
                playerData.put("online", player != null && player.isOnline());
                playerData.put("login_count", info.login_count);
                playerData.put("last_sign_in", info.last_sign_in);
                players.add(playerData);
            }
            response.put("status", "success");
            response.put("players", players);
            response.put("total", rankedList.size());
            response.put("page", page);
            response.put("pageSize", pageSize);
        }
        private void handleListGroups(Map<String, Object> response) {
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
            response.put("status", "success");
            response.put("groups", groups);
        }

        private void handleSetScore(String query, Map<String, Object> response) {
            UUID uuid = UUID.fromString(getQueryParam(query, "player"));
            int score = Integer.parseInt(getQueryParam(query, "score"));
            rankManager.setScoreByUUID(uuid, score);
            response.put("status", "success");
        }

        private void handleSetRank(String query, Map<String, Object> response) {
            UUID uuid = UUID.fromString(getQueryParam(query, "player"));
            String rank = getQueryParam(query, "rank");
            RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
            rankManager.setRankByUUID(uuid, rank, data.getScore(), data.getJoinParticle(), data.getJoinMessage());
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                rankManager.updatePlayerDisplay(player);
                context.getChatListener().updateCache(player);
            }
            response.put("status", "success");
        }

        private void handleSetGroup(String query, Map<String, Object> response) {
            UUID uuid = UUID.fromString(getQueryParam(query, "player"));
            String group = getQueryParam(query, "group");
            rankManager.setGroupByUUID(uuid, group);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                rankManager.updatePlayerDisplay(player);
                context.getChatListener().updateCache(player);
            }
            PlayerData.PlayerInfo info = playerData.getPlayerInfo(Bukkit.getOfflinePlayer(uuid).getName());
            info.groups.clear();
            info.groups.add(group);
            playerData.saveData();
            response.put("status", "success");
        }

        private void handleSetParticle(String query, Map<String, Object> response) {
            UUID uuid = UUID.fromString(getQueryParam(query, "player"));
            String particle = getQueryParam(query, "particle");
            RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
            rankManager.setRankByUUID(uuid, data.getRank(), data.getScore(), Particle.valueOf(particle), data.getJoinMessage());
            response.put("status", "success");
        }

        private void handleSetJoinMessage(String query, Map<String, Object> response) {
            UUID uuid = UUID.fromString(getQueryParam(query, "player"));
            String message = getQueryParam(query, "message");
            RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
            rankManager.setRankByUUID(uuid, data.getRank(), data.getScore(), data.getJoinParticle(), message);
            response.put("status", "success");
        }

        private void handleSetChatColor(String query, Map<String, Object> response) {
            UUID uuid = UUID.fromString(getQueryParam(query, "player"));
            String chatColor = getQueryParam(query, "chat_color");
            RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
            data.setChatColor(chatColor);
            rankManager.setRankByUUID(uuid, data.getRank(), data.getScore(), data.getJoinParticle(), data.getJoinMessage());
            response.put("status", "success");
        }

        private void handleSetDisplayOptions(String query, Map<String, Object> response) {
            UUID uuid = UUID.fromString(getQueryParam(query, "player"));
            boolean showRank = Boolean.parseBoolean(getQueryParam(query, "show_rank"));
            boolean showVip = Boolean.parseBoolean(getQueryParam(query, "show_vip"));
            boolean showGroup = Boolean.parseBoolean(getQueryParam(query, "show_group"));
            boolean showScore = Boolean.parseBoolean(getQueryParam(query, "show_score"));
            RankData data = rankManager.getAllRanks().getOrDefault(uuid, new RankData("member", 0));
            data.setShowRank(showRank);
            data.setShowVip(showVip);
            data.setShowGroup(showGroup);
            data.setShowScore(showScore);
            rankManager.setRankByUUID(uuid, data.getRank(), data.getScore(), data.getJoinParticle(), data.getJoinMessage());
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                rankManager.updatePlayerDisplay(player);
                context.getChatListener().updateCache(player);
            }
            response.put("status", "success");
        }

        private void handleBan(String query, Map<String, Object> response) {
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
        }

        private void handleUnban(String query, Map<String, Object> response) {
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
        }

        private void handleCreateGroup(String query, Map<String, Object> response) {
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
            } catch (Exception e) {
                e.printStackTrace();
            }
            response.put("status", "success");
        }

        private void handleDeleteGroup(String query, Map<String, Object> response) {
            String name = getQueryParam(query, "name");
            rankManager.getGroups().remove(name);
            try (Connection conn = rankManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM `groups` WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            response.put("status", "success");
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
}