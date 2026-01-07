package org.Memo.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.Memo.Entity.ChatRecord;
import org.Memo.Repo.ChatRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRecordService {

    @Value("${app.tz}") private String tz;

    private final ChatRecordRepository repo;
    private final ObjectMapper om = new ObjectMapper();

    // 同一 openid 串行：避免同一行并发追加导致乐观锁重试
    private final ConcurrentHashMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

    public ExecutorService executorFor(String openid) {
        return executors.computeIfAbsent(openid, k ->
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("ws-jsonb-" + k);
                    return t;
                })
        );
    }

    @Transactional
    public ChatRecord createSession(String openid, Instant now) {
        ChatRecord r = ChatRecord.builder()
                .openId(openid)
                .sessionId(UUID.randomUUID())
                .startedAt(now)
                .messageCount(0)
                .lastTs(now)
                .version(0L)
                .build();
        return repo.save(r);
    }

    /**
     * 追加一条消息（乐观锁一次重试）
     */
    @Transactional
    public void append(UUID sessionId, String role, String content, Instant ts) {
        // 读当前版本 & 计算 seq
        ChatRecord cr = repo.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("session not found: " + sessionId));
        long ver = cr.getVersion() == null ? 0L : cr.getVersion();
        int nextSeq = (cr.getMessageCount() == null ? 0 : cr.getMessageCount()) + 1;

        String itemJson;
        try {
            itemJson = om.writeValueAsString(new MsgItem(nextSeq, ts.toString(), role, content));
        } catch (Exception e) {
            throw new IllegalStateException("serialize item failed", e);
        }

        int updated = repo.appendMessage(sessionId, itemJson, ts, ver);
        if (updated == 0) {
            // 乐观锁重试一次
            ChatRecord latest = repo.findBySessionId(sessionId)
                    .orElseThrow(() -> new IllegalStateException("session not found: " + sessionId));
            long ver2 = latest.getVersion();
            int nextSeq2 = (latest.getMessageCount() == null ? 0 : latest.getMessageCount()) + 1;
            try {
                itemJson = om.writeValueAsString(new MsgItem(nextSeq2, ts.toString(), role, content));
            } catch (Exception ignore) {}
            int updated2 = repo.appendMessage(sessionId, itemJson, ts, ver2);
            if (updated2 == 0) {
                throw new IllegalStateException("append conflict twice, give up");
            }
        }
    }

    @Transactional
    public void close(UUID sessionId, Instant now) {
        repo.findBySessionId(sessionId).ifPresent(r -> {
            r.setClosedAt(now);
            repo.save(r);
        });
    }

    // 简单的消息结构（用于序列化到 msgs）
    public record MsgItem(int seq, String ts, String role, String content) {}
    public record MsgItemsSimple(String ts, String content) {}

    public List<MsgItemsSimple> getPreChatByUnionIdAndDay(String unionId) {
        List<MsgItemsSimple> result = new ArrayList<>();
        List<ChatRecord> records = repo.findByUnionIdLimit100(unionId);
        if (records == null || records.isEmpty()) return result;
        for (ChatRecord record : records) {
            String msgs = record.getMsgs();
            List<MsgItem> msgItems = parseMsgs(msgs);
            List<MsgItemsSimple> msgItemsSimple = new ArrayList<>();

            for (MsgItem msgItem : msgItems) {
                if ("user".equals(msgItem.role())) {
                    MsgItemsSimple simpleItem = new MsgItemsSimple(msgItem.ts, msgItem.content);
                    msgItemsSimple.add(simpleItem);
                }
            }
            result.addAll(msgItemsSimple);
        }
        Collections.reverse(result);
        return result;
    }

    public List<MsgItem> parseMsgs(String msgs) {
        if (msgs == null || msgs.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return new ObjectMapper().readValue(msgs, new TypeReference<List<MsgItem>>() {});
        } catch (Exception e) {
            log.info("Failed to parse MsgItem list", e);
            return Collections.emptyList();
        }
    }


}
