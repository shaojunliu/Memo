package org.Memo.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class OkHttpAgentClient implements AgentClient {
    @Value("${agent.url}")
    private String ws_url;

    @Value("${agent.api-key:}")
    private String apiKey;

    @Value("${agent.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${agent.reply-timeout-ms:15000}")
    private int replyTimeoutMs;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WS 长连接读不超时
            .build();

    /** 一问一答：发一条，收第一段回复返回（如需拼接流式，可扩展） */
    public String chat(String openid, String message) {
        String url = trimEnd(ws_url);
        Request.Builder rb = new Request.Builder().url(url);
        if (apiKey != null && !apiKey.isBlank()) {
            rb.addHeader("X-AGENT-KEY", apiKey);  // ✅ 按你要求加到 Header
        }
        Request req = rb.build();

        CountDownLatch done = new CountDownLatch(1);
        StringBuilder buf = new StringBuilder();
        AtomicReference<Throwable> err = new AtomicReference<>();

        WebSocket ws = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                // 看你的 agent 协议：如果收纯文本就发 message；若收 JSON 就改成 JSON
                String payload = "{\"openid\":\"" + esc(openid) + "\",\"message\":\"" + esc(message) + "\"}";
                webSocket.send(payload);
            }
            @Override public void onMessage(WebSocket webSocket, String text) {
                // 简化：收到第一段就返回；如果是分片流式，改为累计并在 onClosing/onClosed 返回
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
            if (!done.await(20, TimeUnit.SECONDS)) {
                ws.cancel();
                throw new RuntimeException("Agent WS timeout");
            }
            if (err.get() != null) throw new RuntimeException("Agent WS failed", err.get());
            return buf.toString();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Agent WS interrupted", ie);
        }
    }

    private static String trimEnd(String s){ return s.endsWith("/") ? s.substring(0, s.length()-1) : s; }
    private static String esc(String s){ return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\""); }
    @SuppressWarnings("unused")
    private static String enc(String s){ return URLEncoder.encode(s==null?"":s, StandardCharsets.UTF_8); }

}
