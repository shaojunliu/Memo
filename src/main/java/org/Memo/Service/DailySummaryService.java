package org.Memo.Service;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.DaillySummarysModel;
import org.Memo.DTO.GetSummaryDetailReq;
import org.Memo.DTO.GetSummaryDetailRes;
import org.Memo.DTO.SummaryModel;
import org.Memo.Entity.ChatRecord;
import org.Memo.Entity.DailyArticleSummaryEntity;
import org.Memo.Repo.DailyArticleSummaryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryService {

    private final DailyArticleSummaryRepository repo;

    public DaillySummarysModel getDailySummary(String openid, String searchStartDay, String searchEndDay) {
        List<DailyArticleSummaryEntity> rows;

        boolean hasRange = (searchStartDay != null && !searchStartDay.isBlank())
                && (searchEndDay != null && !searchEndDay.isBlank());

        if (hasRange) {
            LocalDate start = LocalDate.parse(searchStartDay); // 要求 YYYY-MM-DD
            LocalDate end = LocalDate.parse(searchEndDay);
            if (end.isBefore(start)) {
                // 兜底：交换
                LocalDate tmp = start; start = end; end = tmp;
            }
            LocalDate startDt = LocalDate.from(start.atStartOfDay());
            LocalDateTime endDtInclusive = end.atStartOfDay();
            rows = repo.findByOpenIdAndSummaryDateBetweenOrderBySummaryDateDesc(openid, startDt, LocalDate.from(endDtInclusive));
        } else {
            rows = repo.findByOpenIdOrderBySummaryDateDesc(openid);
        }

        List<SummaryModel> list = rows.stream()
                .map(this::toSummaryModel)
                .toList();

        return DaillySummarysModel.builder()
                .DailySummarySize(String.valueOf(list.size()))
                .summarys(list)
                .build();
    }

    private SummaryModel toSummaryModel(DailyArticleSummaryEntity e) {
        DateParts p = resolveDateParts(e);

        return SummaryModel.builder()
                .articleId(e.getId() == null?"":e.getId().toString())
                .article(e.getArticle() == null ? "" : e.getArticle())
                .articleTitle(e.getArticleTitle() == null ? "" : e.getArticleTitle())
                .moodKeywords(e.getMoodKeywords() == null ? "" : e.getMoodKeywords())
                .actionKeywords(e.getActionKeywords() == null ? "" : e.getActionKeywords())
                .creatTime(e.getSummaryDate().atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli())
                .year(p.year)
                .month(p.month)
                .date(p.date)
                .summaryType("Daily")
                .build();
    }


    /** 优先使用 summaryDate；否则用 createdAt(Instant) 转东八区来拆分 */
    private DateParts resolveDateParts(DailyArticleSummaryEntity e) {
        LocalDate d = e.getSummaryDate();

        // 极端情况兜底：防止旧数据为空
        if (d == null) {
            d = e.getCreatedAt() != null
                    ? e.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();
        }
        String year = String.format("%04d", d.getYear());
        String month = String.format("%02d", d.getMonthValue());
        String date = String.format("%02d", d.getDayOfMonth());
        return new DateParts(year, month, date);
    }

    public List<SummaryModel> getPreSummaryByUnionId(String unionId) throws JsonProcessingException {
        List<SummaryModel> result = new ArrayList<>();
        List<DailyArticleSummaryEntity> articles = repo.findByOpenIdOrderBySummaryDateDesc(unionId);
        log.info("getPreSummaryByUnionId articles: {}", JSON.toJSONString(articles));
        if (articles == null || articles.isEmpty()) return result;
        for (DailyArticleSummaryEntity article : articles) {
            SummaryModel summaryModel = new SummaryModel();
            summaryModel.setArticle(article.getArticle() == null ? "" : article.getArticle());
            summaryModel.setArticleTitle(article.getArticleTitle() == null ? "" : article.getArticleTitle());
            summaryModel.setActionKeywords(article.getActionKeywords() == null ? "" : article.getActionKeywords());
            summaryModel.setMoodKeywords(article.getMoodKeywords() == null ? "" : article.getMoodKeywords());

            String date = article.getSummaryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            long ts = Optional.ofNullable(article.getSummaryDate()).orElse(LocalDate.now())
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            summaryModel.setCreatTime(ts);
            result.add(summaryModel);
        }
        return result;
    }

    private record DateParts(String year, String month, String date) {}
}
