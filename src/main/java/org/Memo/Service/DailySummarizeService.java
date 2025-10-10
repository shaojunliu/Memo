package org.Memo.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.Chat.SummarizeResult;
import org.Memo.Entity.ChatRecord;
import org.Memo.Entity.MsgItem;
import org.Memo.Repo.ChatRecordRepository;
import org.Memo.Repo.DailyArticleSummaryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummarizeService {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ChatRecordRepository chatRepo;
    private final DailyArticleSummaryRepository summaryRepo;
    private final AgentClient agentClient; // 封装HTTP调用Agent
    @Value("${app.tz}") private String tz;

    public void summarizeForDate(LocalDate targetDate) {
        ZoneId zone = ZoneId.of(tz);
        ZonedDateTime start = targetDate.atStartOfDay(zone);
        ZonedDateTime end   = start.plusDays(1);

        List<String> openIds = chatRepo.findDistinctOpenIdsByDay(start, end);
        // 简单并行（注意限速/线程池）
        openIds.parallelStream().forEach(openId -> {
            try {
                if (summaryRepo.existsByOpenIdAndSummaryDate(openId, targetDate)) return; // 幂等跳过

                List<ChatRecord> msgs = chatRepo.findMessagesByOpenIdAndDay(openId, start, end);
                if (msgs == null || msgs.isEmpty()) return;

                String packed = packMessages(msgs, zone);
                SummarizeResult res = agentClient.summarizeDay(openId, packed); // 调Agent：含重试
                summaryRepo.upsertSummary(
                        openId, targetDate,
                        res.getArticle(), res.getMoodKeywords(),
                        res.getModel(), res.getTokenUsageJson()
                );
            } catch (Exception e) {
                // 记录错误并继续其他 open_id
                log.error("summarize fail openId={} date={}", openId, targetDate, e);
            }
        });
    }

    private String packMessages(List<ChatRecord> records, ZoneId zone) {
        StringBuilder sb = new StringBuilder(4096);

        for (ChatRecord record : records) {
            if (record.getMsgs() == null || record.getMsgs().isBlank()) {
                continue;
            }

            try {
                // 解析 jsonb 数组
                List<MsgItem> items = MAPPER.readValue(record.getMsgs(), new TypeReference<>() {});
                for (MsgItem msg : items) {
                    if (msg.content() == null || msg.content().isBlank()) continue;

                    ZonedDateTime zt = msg.ts().atZone(zone);
                    sb.append('[')
                            .append(zt.toLocalDate()).append(' ')
                            .append(zt.toLocalTime().withNano(0))
                            .append("] ")
                            .append(normalizeRole(msg.role())).append(": ")
                            .append(msg.content())
                            .append('\n');
                }
            } catch (Exception e) {
                log.warn("解析 ChatRecord.msgs 失败, openId={}, id={}, 原因={}",
                        record.getOpenId(), record.getId(), e.getMessage());
            }
        }

        return sb.toString();
    }

    private String normalizeRole(String role) {
        if (role == null) return "user";
        String r = role.toLowerCase();
        if (r.contains("assistant")) return "assistant";
        if (r.contains("system")) return "system";
        return "user";
    }
}