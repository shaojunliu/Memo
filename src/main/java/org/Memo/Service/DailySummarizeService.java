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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.util.CollectionUtils;


@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummarizeService {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ChatRecordRepository chatRepo;
    private final DailyArticleSummaryRepository summaryRepo;
    private final AgentClient agentClient; // 封装HTTP调用Agent
    @Value("${app.tz}") private String tz;

    public void summarizeForDate(LocalDate targetDate, List<String> orderUnionIds) {
        ZoneId zone = ZoneId.of(tz);
        Instant start = targetDate.atStartOfDay(zone).toInstant();
        Instant end   = targetDate.atStartOfDay(zone).plusDays(1).toInstant();
        log.info("summarizeForDate start:{} end:{}", start, end);

        // 1) 定时批量：orderUnionId 为空 -> 找当天所有 unionId，已存在则跳过（幂等）
        // 2) 手动触发：指定 orderUnionId -> 只跑该 unionId，并强制覆盖（即使已存在也会重算并 upsert）
        final boolean manualOverride = !CollectionUtils.isEmpty(orderUnionIds);

        List<String> unionIds;
        if (manualOverride) {
            unionIds = orderUnionIds;
        } else {
            unionIds = chatRepo.findDistinctOpenIdsByDay(start, end);
        }

        log.info("summarizeForDate date={} manualOverride={} unionIds={}", targetDate, manualOverride, unionIds);

        // 简单并行（注意限速/线程池）。手动触发通常只 1 个 unionId。
        unionIds.parallelStream().forEach(unionId -> {
            try {
                // 定时批量才做幂等跳过；手动触发要允许覆盖旧结果
                if (!manualOverride && summaryRepo.existsByOpenIdAndSummaryDate(unionId, targetDate)) {
                    return;
                }

                log.info("summarizeForDate processing unionId={} date={} manualOverride={}", unionId, targetDate, manualOverride);

                List<ChatRecord> msgs = chatRepo.findMessagesByOpenIdAndDay(unionId, start, end);
                if (msgs == null || msgs.isEmpty()) {
                    log.info("summarizeForDate no msgs, unionId={} date={}", unionId, targetDate);
                    return;
                }

                String packed = packMessages(msgs, zone);
                SummarizeResult res = agentClient.summarizeDay(unionId, packed); // 调Agent：含重试

                // 兜底：避免把原有总结覆盖成空
                if (res == null || StringUtils.isBlank(res.getArticle())) {
                    log.warn("skip upsert: empty article, unionId={} date={} manualOverride={}", unionId, targetDate, manualOverride);
                    return;
                }

                // upsert 本身应覆盖旧内容；手动触发会强制走到这里
                summaryRepo.upsertSummary(
                        unionId, targetDate,
                        res.getArticle(), res.getMoodKeywords(), res.getActionKeywords(), res.getMemoryPoint(), res.getAnalyzeResult(), res.getArticleTitle(),
                        res.getModel(), Optional.ofNullable(res.getTokenUsageJson()).orElse("{}")
                );

                log.info("summarizeForDate upsert ok, unionId={} date={} manualOverride={}", unionId, targetDate, manualOverride);
            } catch (Exception e) {
                log.error("summarize fail unionId={} date={} manualOverride={}", unionId, targetDate, manualOverride, e);
            }
        });
    }


    /**
     * 批量刷新指定日期列表的所有用户总结（每个日期都会强制重算并 upsert 覆盖旧结果）。
     */
    public void summarizeForDates(List<LocalDate> targetDates) {
        if (CollectionUtils.isEmpty(targetDates)) {
            log.info("summarizeForDates empty targetDates");
            return;
        }
        // 逐日执行，避免并发过高导致 Agent/DB 压力过大；如需更高并行度可在外层控制线程池。
        for (LocalDate d : targetDates) {
            if (d == null) {
                continue;
            }
            summarizeForDate(d, null);
        }
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