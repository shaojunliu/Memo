package org.Memo.Controller.ws;

import io.jsonwebtoken.io.IOException;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.Memo.Entity.ChatRecord;
import org.Memo.Repo.ChatRecordRepository;
import org.Memo.Service.AgentClient;
import org.Memo.Service.OkHttpAgentClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@ServerEndpoint("/ws/chat")
@Component
@Slf4j
public class ChatWebSocket {

    @Autowired
    private OkHttpAgentClient agentClient;

    @Autowired
    private ChatRecordRepository chatRecordRepo;

    @OnMessage
    public void onMessage(Session session, String message) throws IOException {
        // 调 Agent
        String reply = agentClient.chat("uid",message);

        // 存数据库
        ChatRecord record = new ChatRecord();
        record.setUserMessage(message);
        record.setAgentReply(reply);

        Instant now = Instant.now();
        ZonedDateTime timeStamp = now.atZone(ZoneId.of("Asia/Shanghai"));

        record.setCreatedAt(timeStamp.toInstant());
        chatRecordRepo.save(record);

        // 推送给前端
        if (session != null && session.isOpen()) {
            session.getAsyncRemote().sendText(reply, result -> {
                if (!result.isOK()) {
                    log.error("发送消息失败", result.getException());
                }
            });
        }
    }

}
