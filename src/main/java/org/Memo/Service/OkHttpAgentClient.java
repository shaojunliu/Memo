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
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;

    public OkHttpAgentClient(
            @Value("${agent.base-url}") String baseUrl,
            @Value("${agent.api-key:}") String apiKey,
            @Value("${agent.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${agent.read-timeout-ms:8000}") int readTimeoutMs
    ) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public String chat(String userId, String message) {
        try {
            ChatRequest req = new ChatRequest();
            req.setUserId(userId);
            req.setMessage(message);

            String json = mapper.writeValueAsString(req);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

            Request.Builder rb = new Request.Builder()
                    .url(baseUrl + "/ws/chat")
                    .post(body);

            boolean apiKeyUnEmpty = !StringUtils.isEmpty(apiKey);
            if (apiKeyUnEmpty) {
                rb.addHeader("X-AGENT-KEY", apiKey);
            }

            try (Response resp = client.newCall(rb.build()).execute()) {
                if (!resp.isSuccessful()) {
                    throw new RuntimeException("Agent HTTP " + resp.code() + " - " + resp.message());
                }
                String respStr = resp.body() != null ? resp.body().string() : "";
                // 若 Python 直接返回纯文本：
                if (!respStr.trim().startsWith("{")) return respStr;

                // 若返回 JSON，解析为 ChatResponse：
                ChatResponse cr = mapper.readValue(respStr, ChatResponse.class);
                return cr.getReply();
            }
        } catch (Exception e) {
            // 这里可以结合你的日志/告警体系
            throw new RuntimeException("Call Agent failed", e);
        }
    }


}
