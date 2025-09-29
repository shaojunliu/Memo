package org.Memo.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.*;
import org.Memo.DTO.Chat.ChatRequest;
import org.Memo.DTO.Chat.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Component
public class OkHttpAgentClient implements AgentClient {
    @Value("${app.agent.url}")
    private String ws_url;

    @Value("${app.agent.api-key:}")
    private String apiKey;

    @Value("${app.agent.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${app.agent.reply-timeout-ms:15000}")
    private int replyTimeoutMs;

    private final OkHttpClient wsClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WS 长连接读不超时
            .build();

    @Override
    public String chat(String userId, String message) {
        String url = trimEnd(ws_url);
        String json = "{\"openid\":\"" + escape(userId) + "\",\"message\":\"" + escape(message) + "\"}";
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, MediaType.parse("application/json")));

        if (apiKey != null && !apiKey.isBlank()) {
            builder.addHeader("X-AGENT-KEY", apiKey);
        }
        try {
            Request req = builder.build();

            try (Response resp = wsClient.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    throw new RuntimeException("Agent HTTP " + resp.code() + " - " + resp.message() + " " + body);
                }
                return resp.body().string();
            } catch (Exception e) {
                throw new RuntimeException("Call Agent failed", e);
            }

        } catch (Exception e) {
            // 这里可以结合你的日志/告警体系
            throw new RuntimeException("Call Agent failed", e);
        }
    }
    private static String trimEnd(String s){ return s.endsWith("/") ? s.substring(0, s.length()-1) : s; }
    private static String normalize(String p){ return p.startsWith("/") ? p : "/" + p; }
    private static String escape(String s){ return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\""); }


}
