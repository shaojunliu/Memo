package org.Memo.Service;



import lombok.RequiredArgsConstructor;
import org.Memo.Entity.DailyArticleSummaryEntity;
import org.Memo.Repo.DailyArticleSummaryRepository;
import org.Memo.DTO.GetSummaryDetailReq;
import org.Memo.DTO.GetSummaryDetailRes;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SummaryDetailService {
    private static final ZoneId CN_TZ = ZoneId.of("Asia/Shanghai");

    private final DailyArticleSummaryRepository dailyRepo;
    public GetSummaryDetailRes getDetail(GetSummaryDetailReq req) {
        String type = req.getSummaryType() == null ? "daily" : req.getSummaryType().toLowerCase(Locale.ROOT);
        switch (type) {
        case "daily" -> {
            DailyArticleSummaryEntity e = dailyRepo.findOneById(req.getArticleId())
                    .orElseThrow(() -> new IllegalArgumentException("记录不存在: id=" + req.getArticleId()));

            long createTime = e.getSummaryDate()
                    .atStartOfDay(CN_TZ)
                    .toInstant()
                    .toEpochMilli();

            String createDate = e.getSummaryDate().toString(); // yyyy-MM-dd

            return GetSummaryDetailRes.builder()
                    .summaryType("daily")                                     // 按请求回传
                    .articleTitle(nvl(e.getArticleTitle()))
                    .createTime(createTime)
                    .createDate(createDate)
                    .content(nvl(e.getArticle()))
                    .moodKeywords(nvl(e.getMoodKeywords()))
                    .actionKeywords(nvl(e.getActionKeywords()))
                    .memoryPoint(nvl(e.getMemoryPoint()))
                    .analyzeResult(nvl(e.getAnalyzeResult()))
                    .build();
        }
        default -> throw new UnsupportedOperationException("暂不支持的 summaryType: " + type);
    }
}

    private static String nvl(String s) { return s == null ? "" : s; }

}

