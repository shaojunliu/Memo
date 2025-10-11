package org.Memo.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.Memo.DTO.Chat.SummarizeResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import com.fasterxml.jackson.core.type.TypeReference;


@Slf4j
@Component
public class OkHttpAgentClient implements AgentClient {
    @Value("${agent.url}")
    private String ws_url;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WS 长连接读不超时
            .build();

    // 与我们之前约定的输出格式对应的正则解析：
    // # 每日总结\n(正文)\n# 今日情绪关键词\n词1, 词2, 词3
    private static final Pattern P_SUMMARY = Pattern.compile(
            "#\\s*每日总结\\s*(.+?)#\\s*今日情绪关键词",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern P_MOOD = Pattern.compile(
            "#\\s*今日情绪关键词\\s*(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Value("${agent.ws.timeoutSeconds:60}")
    private int timeoutSeconds;

    @jakarta.annotation.PostConstruct
    void logCfg() {
        log.info("WS cfg baseUrl={}",ws_url);
        if (!ws_url.startsWith("ws://") && !ws_url.startsWith("wss://")) {
            throw new IllegalStateException("app.agent.base-url 必须是 ws:// 或 wss://，当前=" + ws_url);
        }
    }
    /** 一问一答：发一条，收第一段回复返回（如需拼接流式，可扩展） */
    public String chat(String openid, String message) {
        String payload = "{\"openid\":\"" + esc(openid) + "\",\"message\":\"" + esc(message) + "\"}";
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
        SummarizeResult defaultSummarizeResult = new SummarizeResult();
        defaultSummarizeResult.setArticle("Agent 返回空响应");
        defaultSummarizeResult.setMoodKeywords("sad,sad,sad");
        try {
            // 建议的统一协议：带 type，便于 Agent 路由
            Map<String, Object> req = Map.of(
                    "type", "daily_summary",
                    "openid", openId == null ? "" : openId,
                    "text", packedText
            );
            String reqJson = MAPPER.writeValueAsString(req);

            String resp = sendAndWaitOnce(reqJson, Duration.ofSeconds(timeoutSeconds));
            if (resp == null || resp.isBlank()) {
                return defaultSummarizeResult;
            }

            // 1) 首选：JSON 结果
            if (looksLikeJson(resp)) {
                try {
                    return MAPPER.readValue(resp, SummarizeResult.class);
                } catch (Exception jsonEx) {
                    // 兼容某些 Agent 返回键名不同的情况（可做一次小型映射）
                    Map<String, Object> m = MAPPER.readValue(resp, new TypeReference<Map<String, Object>>() {});
                    String article = str(Optional.ofNullable(m.get("article")).orElse("Agent 返回空响应"));
                    String mood    = str(Optional.ofNullable("sad,sad,sad"));
                    return new SummarizeResult(article, mood, "default", "");
                }
            }

            // 2) 兼容：纯文本（严格遵循我们约定的输出格式）
            return parsePlaintextToResult(resp);

        } catch (Exception e) {
            return defaultSummarizeResult;
        }
    }

    private SummarizeResult parsePlaintextToResult(String text) {
        String article = "parsePlaintextToResult error";
        String mood = "";
        try {
            Matcher m1 = P_SUMMARY.matcher(text);
            if (m1.find()) {
                article = m1.group(1).trim();
            }
            Matcher m2 = P_MOOD.matcher(text);
            if (m2.find()) {
                mood = m2.group(1).trim();
            }
        } catch (Exception ignore) { /* 保底 */ }

        if ((article == null || article.isBlank()) && looksLikeJson(text)) {
            // 再保底一次：有些服务端会把 JSON 当文本发出
            try {
                return MAPPER.readValue(text, SummarizeResult.class);
            } catch (Exception ignored) {}
        }
        return new SummarizeResult(article, mood, null, null);
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

    private static boolean looksLikeJson(String s) {
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }
    private static String trimEnd(String s){ return s.endsWith("/") ? s.substring(0, s.length()-1) : s; }
    private static String esc(String s){ return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\""); }
    @SuppressWarnings("unused")
    private static String enc(String s){ return URLEncoder.encode(s==null?"":s, StandardCharsets.UTF_8); }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}
