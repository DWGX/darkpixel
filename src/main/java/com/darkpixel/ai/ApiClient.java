package com.darkpixel.ai;

import com.darkpixel.Global;
import com.darkpixel.manager.ConfigManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class ApiClient {
    private final HttpClient client = HttpClient.newBuilder().build();
    private final ConfigManager config;
    private final Global context;

    public ApiClient(ConfigManager config, Global context) {
        this.config = config;
        this.context = context;
    }

    public CompletableFuture<String> chatCompletionAsync(String msg, String model, int maxTokens) {
        return CompletableFuture.supplyAsync(() -> chatCompletion(msg, model, maxTokens))
                .exceptionally(t -> "§cAPI异常: " + (t != null ? t.getMessage() : "未知错误，请稍后再试"));
    }

    private String chatCompletion(String msg, String model, int maxTokens) {
        String prompt = config.getSystemPrompt() != null ? config.getSystemPrompt() + "\n对话历史（如果有）:\n" + msg : msg;
        if (prompt == null) {
            context.getPlugin().getLogger().severe("System prompt is null or empty. Please check config.yml!");
            return "§c系统提示配置为空，请检查 config.yml！";
        }
        context.getPlugin().getLogger().info("发送给 API 的完整提示词: " + prompt);
        String[] urls = {"https://api.deepseek.com", "https://api.deepseek.com/v1"};
        context.getPlugin().getLogger().info("Attempting API request with model: " + model + ", prompt length: " + prompt.length());

        for (String url : urls) {
            for (int retry = 0; retry < 3; retry++) {
                try {
                    String body = buildRequestBody(prompt, model, maxTokens);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url + "/chat/completions"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + (config.getApiKey() != null ? config.getApiKey() : ""))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    context.getPlugin().getLogger().info("API response status: " + resp.statusCode() + " for URL: " + url);
                    context.getPlugin().getLogger().info("API 返回的完整响应: " + resp.body());
                    switch (resp.statusCode()) {
                        case 200:
                            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                            JsonObject choice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
                            if (choice != null && choice.has("message")) {
                                return choice.getAsJsonObject("message").get("content").getAsString();
                            }
                            return "/say " + config.getAiName() + "API返回格式错误 AI: 请稍后重试";
                        case 401:
                            context.getPlugin().getLogger().warning("API Key 无效，请检查 config.yml 中的 api_key 配置");
                            return "§cAPI Key 无效，请联系服务器管理员！";
                        case 402:
                            return "§c余额不足，请联系管理员充值！";
                        case 503:
                            context.getPlugin().getLogger().warning("API temporary unavailable (503), retrying... Attempt " + (retry + 1));
                            try {
                                Thread.sleep(2000 * (retry + 1)); // 增加重试间隔
                            } catch (InterruptedException ie) {
                                return "§c重试中断，请稍后再试！";
                            }
                            continue;
                        default:
                            return "§cAPI返回状态码 " + resp.statusCode() + "，请稍后再试！";
                    }
                } catch (Exception e) {
                    context.getPlugin().getLogger().severe("API request failed: " + e.getMessage());
                    if (retry < 2) {
                        try {
                            Thread.sleep(2000 * (retry + 1));
                        } catch (InterruptedException ie) {
                            return "§c重试中断，请稍后再试！";
                        }
                        continue;
                    }
                    return "§cAPI连接失败，已重试3次，请联系管理员！";
                }
            }
        }
        return "§c所有API请求均失败，请检查网络或联系管理员！";
    }

    private String buildRequestBody(String prompt, String model, int maxTokens) {
        return String.format("{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"stream\":false,\"max_tokens\":%d,\"temperature\":0.7,\"top_p\":0.9}",
                model, prompt.replace("\n", "\\n").replace("\"", "\\\""), "请根据系统提示和用户信息生成回复", maxTokens);
    }
}