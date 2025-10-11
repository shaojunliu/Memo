package org.Memo.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.common.util.StringUtils;
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

import java.time.*;
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
        Instant start = targetDate.atStartOfDay(zone).toInstant();
        Instant end   = targetDate.atStartOfDay(zone).plusDays(1).toInstant();
        log.info("summarizeForDate start:{} end:{}", start, end);
        List<String> openIds = chatRepo.findDistinctOpenIdsByDay(start, end);
        log.info("openIds:{}", openIds);
        // 简单并行（注意限速/线程池）
        openIds.parallelStream().forEach(openId -> {
            try {
                if (summaryRepo.existsByOpenIdAndSummaryDate(openId, targetDate)) return; // 幂等跳过

                log.info("summarizeForDate openId unexists:{}", openId);
                List<ChatRecord> msgs = chatRepo.findMessagesByOpenIdAndDay(openId, start, end);
                log.info("msgs:{}", msgs);
                if (msgs == null || msgs.isEmpty()) return;

                String packed = packMessages(msgs, zone);
                log.info("packed:{}", packed);
                SummarizeResult res = agentClient.summarizeDay(openId, packed); // 调Agent：含重试
                log.info("res:{}", res);
                // 新增的兜底
                if (res == null || StringUtils.isBlank(res.getArticle())) {
                    log.warn("skip upsert: empty article, openId={} date={}", openId, targetDate);
                    return;
                }
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
            String raw = record.getMsgs();
            if (raw == null || raw.isBlank()) continue;

            try {
                JsonNode root = MAPPER.readTree(raw);
                if (!root.isArray()) continue;

                for (JsonNode n : root) {
                    try {
                        // 逐字段容错读取
                        String content = textOrNull(n.get("content"));
                        if (content == null || content.isBlank()) continue;

                        String role = textOrNull(n.get("role"));
                        String tsStr = textOrNull(n.get("ts"));
                        if (tsStr == null || tsStr.isBlank()) continue;

                        // 关键：用 Instant.parse 直接吃标准 ISO-8601（支持纳秒）
                        Instant ts = parseInstant(tsStr);
                        if (ts == null) continue;

                        ZonedDateTime zt = ts.atZone(zone);
                        sb.append('[')
                                .append(zt.toLocalDate()).append(' ')
                                .append(zt.toLocalTime().withNano(0))
                                .append("] ")
                                .append(normalizeRole(role)).append(": ")
                                .append(content)
                                .append('\n');

                    } catch (Exception perItem) {
                        // 单条坏数据跳过，不影响其它
                        log.warn("skip bad msg item in record id={}, cause={}",
                                record.getId(), perItem.toString());
                    }
                }
            } catch (Exception e) {
                log.warn("解析 ChatRecord.msgs 失败, openId={}, id={}, 原因={}",
                        record.getOpenId(), record.getId(), e.getMessage());
            }
        }

        return sb.toString();
    }

    // ------- helpers --------
    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private String normalizeRole(String role) {
        if (role == null) return "user";
        String r = role.toLowerCase();
        if (r.contains("assistant")) return "assistant";
        if (r.contains("system")) return "system";
        return "user";
    }

    private static Instant parseInstant(String s) {
        try {
            // 标准路径：ISO-8601（支持 0~9 位小数 + 'Z' 或带偏移）
            return Instant.parse(s);
        } catch (Exception e1) {
            try {
                // 兼容带偏移但不以 Z 结尾的情况
                return OffsetDateTime.parse(s).toInstant();
            } catch (Exception e2) {
                return null;
            }
        }
    }
}