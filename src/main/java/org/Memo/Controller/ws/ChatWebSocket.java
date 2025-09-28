package org.Memo.Controller.ws;

import java.io.IOException;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.Memo.Config.SpringEndpointConfigurator;
import org.Memo.Entity.ChatRecord;
import org.Memo.Repo.ChatRecordRepository;
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

@ServerEndpoint(value = "/ws/chat/{openid}", configurator = SpringEndpointConfigurator.class)
@Component
@Slf4j
public class ChatWebSocket {

    @Autowired
    private OkHttpAgentClient agentClient;

    @Autowired
    private ChatRecordRepository chatRecordRepo;

    // 如果一个 openid 可能同时多端在线，用 Set<Session>；否则可用 Map<SessionId, Session>
    private static final Map<String, Set<Session>> OPENID_SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("openid") String openid) {
        // 这里可做最简校验（长度/前缀），需要更严可改为签名校验
        if (StringUtils.isEmpty(openid)) {
            close(session, "invalid openid");
            return;
        }
        session.getUserProperties().put("openid", openid);
        OPENID_SESSIONS.computeIfAbsent(openid, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WS connected: openid={}, session={}", openid, session.getId());
    }

    @OnMessage
    public void onMessage(Session session, String message) throws IOException {
        String openid = (String) session.getUserProperties().get("openid");
        if (openid == null) { close(session, "unauthorized"); return; }

        // 调 Agent（把 openid 透传给你的后端，做个性化）
        String reply = agentClient.chat(openid, message);

        // 落库
        ChatRecord record = new ChatRecord();
        record.setOpenId(openid);                 // ✅ 记上 openid
        record.setUserMessage(message);
        record.setAgentReply(reply);
        ZonedDateTime ts = Instant.now().atZone(ZoneId.of("Asia/Shanghai"));
        record.setCreatedAt(ts.toInstant());
        chatRecordRepo.save(record);

        // 回给当前连接
        if (session.isOpen()) {
            session.getAsyncRemote().sendText(reply, result -> {
                if (!result.isOK()) log.error("发送消息失败", result.getException());
            });
        }
    }


    @OnClose
    public void onClose(Session session, CloseReason reason) {
        String openid = (String) session.getUserProperties().get("openid");
        if (openid != null) {
            Set<Session> set = OPENID_SESSIONS.get(openid);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) OPENID_SESSIONS.remove(openid);
            }
        }
        log.info("WS closed: openid={}, session={}, reason={}", openid, session.getId(), reason);
    }

    @OnError
    public void onError(Session session, Throwable t) {
        log.error("WS error, session={}", session == null ? "null" : session.getId(), t);
    }

    private static void close(Session s, String reason) {
        try { s.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason)); }
        catch (Exception ignored) {}
    }


}
