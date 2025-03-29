package com.darkpixel.utils;

import com.darkpixel.Global;
import com.darkpixel.rank.RankData;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.bukkit.Bukkit;
import org.bukkit.Particle;

public class RankServerClient implements Runnable {
    private final String baseUrl;
    private final HttpClient client;
    private final Gson gson;
    private final Global context;
    private volatile boolean running = true;
    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private Thread syncThread;

    public RankServerClient(String baseUrl, Global context) {
        this.baseUrl = baseUrl;
        this.context = context;
        this.client = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.syncThread = new Thread(this, "RankServerClient-SyncThread");
        this.syncThread.start();
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                taskQueue.take().run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Bukkit.getLogger().severe("RankServerClient sync task failed: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        running = false;
        if (syncThread != null) {
            syncThread.interrupt();
            try {
                syncThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public Map<String, Object> listPlayers(int page, int pageSize) throws IOException, InterruptedException {
        String url = baseUrl + "/api/list_players?page=" + page + "&pageSize=" + pageSize;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), Map.class);
    }

    public void setRank(String uuid, String rank) {
        taskQueue.offer(() -> {
            try {
                String url = baseUrl + "/api/set_rank?player=" + uuid + "&rank=" + rank;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                Bukkit.getLogger().severe("Failed to set rank for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public void setScore(String uuid, int score) {
        taskQueue.offer(() -> {
            try {
                String url = baseUrl + "/api/set_score?player=" + uuid + "&score=" + score;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                Bukkit.getLogger().severe("Failed to set score for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public void setGroup(String uuid, String group) {
        taskQueue.offer(() -> {
            try {
                String url = baseUrl + "/api/set_group?player=" + uuid + "&group=" + group;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                Bukkit.getLogger().severe("Failed to set group for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public void syncAllPlayers() {
        taskQueue.offer(() -> {
            try {
                Map<String, Object> playersData = listPlayers(1, Integer.MAX_VALUE);
                if (playersData != null && "success".equals(playersData.get("status"))) {
                    List<Map<String, Object>> players = (List<Map<String, Object>>) playersData.get("players");
                    for (Map<String, Object> player : players) {
                        String uuidStr = (String) player.get("uuid");
                        UUID uuid = UUID.fromString(uuidStr);
                        RankData data = context.getRankManager().getAllRanks().getOrDefault(uuid, new RankData("member", 0));
                        data.setRank((String) player.get("rank"));
                        data.setScore(((Number) player.get("score")).intValue());
                        data.setJoinParticle(Particle.valueOf((String) player.get("join_particle")));
                        data.setJoinMessage((String) player.get("join_message"));
                        context.getRankManager().getAllRanks().put(uuid, data);
                    }
                    Bukkit.getLogger().info("Synced player data from RankServer");
                }
            } catch (IOException | InterruptedException e) {
                Bukkit.getLogger().severe("Failed to sync all players: " + e.getMessage());
            }
        });
    }
}