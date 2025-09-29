package org.Memo.Controller.ws;

import java.io.IOException;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.Memo.Config.SpringEndpointConfigurator;
import org.Memo.Service.ChatRecordService;
import org.Memo.Service.OkHttpAgentClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.UUID;

@ServerEndpoint(value = "/ws/chat/{openid}", configurator = SpringEndpointConfigurator.class)
@Component
@Slf4j
public class ChatWebSocket {

    @Autowired
    private ChatRecordService recordService;

    @Autowired
    private OkHttpAgentClient agentClient;

    // 如果一个 openid 可能同时多端在线，用 Set<Session>；否则可用 Map<SessionId, Session>
    private static final Map<String, Set<Session>> OPENID_SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("openid") String openid) {
        // 这里可做最简校验（长度/前缀），需要更严可改为签名校验
        if (StringUtils.isEmpty(openid)) {
            close(session, "invalid openid");
            return;
        }

        var now = Instant.now();
        var rec = recordService.createSession(openid, now);   // 新建单表记录
        UUID sessionId = rec.getSessionId();
        session.getUserProperties().put("openid", openid);
        session.getUserProperties().put("sessionId", sessionId);

        // 下发 ack（客户端保存 sessionId，后续展示用）
        send(session, "{\"type\":\"sessionAck\",\"sessionId\":\"" + sessionId + "\"}");
        // 选配：设置空闲超时，比如 10 分钟
        session.setMaxIdleTimeout(10 * 60 * 1000L);

        log.info("WS connected openid={}, sessionId={}", openid, sessionId);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        String openid = (String) session.getUserProperties().get("openid");
        UUID sessionId = (UUID) session.getUserProperties().get("sessionId");
        if (openid == null || sessionId == null) {
            close(session, "unauthorized"); return;
        }

        // 串行执行：同一 openid 的消息严格顺序
        recordService.executorFor(openid).submit(() -> {
            Instant now = Instant.now();

            // 1) 先把 user 消息追加进该会话
            recordService.append(sessionId, "user", message, now);

            // 2) 调用 Agent（非流式）
            String reply;
            try {
                reply = agentClient.chat(openid, message);
                if (reply == null) reply = "";
            } catch (Exception e) {
                log.error("agent error", e);
                reply = "（服务异常，请稍后再试）";
            }

            // 3) 把 assistant 回复也追加
            recordService.append(sessionId, "assistant", reply, Instant.now());

            // 4) 推回客户端
            send(session, reply);

            // 5) （可选）达到阈值就结束会话，客户端自动重连新会话
            int MAX_COUNT = 200;
            // 轻量做法：这里不再读库；你也可以在 append 返回最新 count，再判断。
            // 为简化，这里省略阈值判断的落库查询逻辑。
        });
    }


    @OnClose
    public void onClose(Session session, CloseReason reason) {
        UUID sid = (UUID) session.getUserProperties().get("sessionId");
        if (sid != null) recordService.close(sid, Instant.now());
        log.info("WS closed sid={}, reason={}", sid, reason);
    }

    @OnError
    public void onError(Session session, Throwable t) {
        log.error("WS error", t);
    }

    private static void send(Session s, String text) {
        if (s != null && s.isOpen()) {
            s.getAsyncRemote().sendText(text, r -> {});
        }
    }
    private static void close(Session s, String reason) {
        try { s.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason)); } catch (Exception ignore) {}
    }

}
