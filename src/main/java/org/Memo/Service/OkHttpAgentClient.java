package org.Memo.Service;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.Memo.DTO.Chat.ChatRequest;
import org.Memo.DTO.Chat.SummarizeResult;
import org.Memo.DTO.SummaryModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;


@Slf4j
@Component
public class OkHttpAgentClient implements AgentClient {
    @Value("${agent.url}")
    private String ws_url;

    private String chat_url;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WS 长连接读不超时
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Value("${agent.ws.timeoutSeconds:300}")
    private int timeoutSeconds;

    @jakarta.annotation.PostConstruct
    void logCfg() {
        log.info("WS cfg baseUrl={}",ws_url);
        if (!ws_url.startsWith("ws://") && !ws_url.startsWith("wss://")) {
            throw new IllegalStateException("app.agent.base-url 必须是 ws:// 或 wss://，当前=" + ws_url);
        }
    }
    /** 一问一答：发一条，收第一段回复返回（如需拼接流式，可扩展） */
    public String chat(String openid, String message, List<ChatRecordService.MsgItem> preChat, List<SummaryModel> preDailySummary) {
        ChatRequest  chatRequest = new ChatRequest();
        chatRequest.setOpenid(openid);
        chatRequest.setMessage(message);
        chatRequest.setPreChat(preChat);
        chatRequest.setPreDailySummary(preDailySummary);
        String payload = JSON.toJSONString(chatRequest);
        return sendAndWaitOnce(payload, Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public String chatWs(String openid, String message, List<ChatRecordService.MsgItem> preChat, List<SummaryModel> preDailySummary) {
        ChatRequest  chatRequest = new ChatRequest();
        chatRequest.setOpenid(openid);
        chatRequest.setMessage(message);
        chatRequest.setPreChat(preChat);
        chatRequest.setPreDailySummary(preDailySummary);
        String payload = JSON.toJSONString(chatRequest);
        return sendAndWaitOnce(payload, Duration.ofSeconds(timeoutSeconds));
    }

    /**
     * 通过 WS 调用 Agent 做“每日总结”，返回 SummarizeResult。
     * Agent 端推荐返回 JSON：
     * { "article": "...", "moodKeywords": "专注, 放松, 期待", "model": "gpt-4.1-mini", "tokenUsageJson": "{...}" }
     * 如返回纯文本（带“# 每日总结 / # 今日情绪关键词”），也会自动解析。
     */
    @Override
    public SummarizeResult summarizeDay(String openId, String packedText) {
        SummarizeResult defaultResult = new SummarizeResult("Agent 返回空响应", "sad,sad,sad","none,none,none","title", "qwen", "{}");

        try {
            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper MAPPER = new ObjectMapper();

            Map<String, Object> req = Map.of(
                    "type", "daily_summary",
                    "openid", openId == null ? "" : openId,
                    "text", packedText
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String reqJson = MAPPER.writeValueAsString(req);

            log.info("reqJson={}", reqJson);
            HttpEntity<String> entity = new HttpEntity<>(reqJson, headers);

            // 调用 Agent 服务
            String url = "http://agent:8001/summary/daily";
            String resp = restTemplate.postForObject(url, entity, String.class);

            if (resp == null || resp.isBlank()) return defaultResult;

            // 尝试 JSON 解析
            if (resp.trim().startsWith("{")) {
                try {
                    return MAPPER.readValue(resp, SummarizeResult.class);
                } catch (Exception jsonEx) {
                    Map<String, Object> m = MAPPER.readValue(resp, new TypeReference<Map<String, Object>>() {});
                    String article = Optional.ofNullable(m.get("article")).map(Object::toString).orElse(defaultResult.getArticle());
                    String mood = Optional.ofNullable(m.get("moodKeywords")).map(Object::toString).orElse(defaultResult.getMoodKeywords());
                    String action = Optional.ofNullable(m.get("actionKeywords")).map(Object::toString).orElse(defaultResult.getActionKeywords());
                    String title = Optional.ofNullable(m.get("articleTitle")).map(Object::toString).orElse(defaultResult.getArticleTitle());
                    String model = Optional.ofNullable(m.get("model")).map(Object::toString).orElse("default");
                    String tokenUsageJson = Optional.ofNullable(m.get("tokenUsageJson")).map(Object::toString).orElse("");
                    return new SummarizeResult(article, mood,action,title, model, tokenUsageJson);
                }
            }
            return defaultResult;

        } catch (Exception e) {
            e.printStackTrace();
            return defaultResult;
        }
    }


    // ========= 公共 WS 发送/等待 =========

    /** 发送一段文本请求，等待一次回复（第一段 onMessage 即返回），然后主动 close */
    private String sendAndWaitOnce(String payload, Duration timeout) {
        String url = trimEnd(ws_url);
        Request req = new Request.Builder().url(url).build();

        CountDownLatch done = new CountDownLatch(1);
        StringBuilder buf = new StringBuilder();
        AtomicReference<Throwable> err = new AtomicReference<>();

        WebSocket ws = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send(payload);
            }
            @Override public void onMessage(WebSocket webSocket, String text) {
                // 如果服务端是流式分片，这里可改为累计并在 onClosing/onClosed 一起返回
                buf.append(text);
                webSocket.close(1000, "done");
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                err.set(t); done.countDown();
            }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                done.countDown();
            }
        });

        try {
            if (!done.await(timeout.toSeconds(), TimeUnit.SECONDS)) {
                ws.cancel();
                throw new RuntimeException("Agent WS timeout (" + timeout.toSeconds() + "s)");
            }
            if (err.get() != null) throw new RuntimeException("Agent WS failed", err.get());
            return buf.toString();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Agent WS interrupted", ie);
        }
    }

    /**
     * 使用 HTTP 调用 Agent，一问一答：发一段 JSON，返回完整字符串响应
     */
    private String sendHttpOnce(String payload) {
        String httpUrl = toHttpUrl(trimEnd(chat_url));
        RequestBody body = RequestBody.create(
                payload,
                okhttp3.MediaType.parse("application/json; charset=utf-8")
        );
        Request request = new Request.Builder()
                .url(httpUrl)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Agent HTTP failed, code=" + response.code());
            }
            ResponseBody respBody = response.body();
            return respBody != null ? respBody.string() : "";
        } catch (Exception e) {
            throw new RuntimeException("Agent HTTP error", e);
        }
    }

    private static String toHttpUrl(String wsUrl) {
        if (wsUrl == null) {
            return null;
        }
        if (wsUrl.startsWith("ws://")) {
            return "http://" + wsUrl.substring(5);
        }
        if (wsUrl.startsWith("wss://")) {
            return "https://" + wsUrl.substring(6);
        }
        return wsUrl;
    }



    private static String trimEnd(String s){ return s.endsWith("/") ? s.substring(0, s.length()-1) : s; }

    @SuppressWarnings("unused")
    private static String enc(String s){ return URLEncoder.encode(s==null?"":s, StandardCharsets.UTF_8); }

}
