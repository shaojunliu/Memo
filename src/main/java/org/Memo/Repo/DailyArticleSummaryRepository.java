package org.Memo.Repo;

import org.Memo.Entity.DailyArticleSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

public interface DailyArticleSummaryRepository extends JpaRepository<DailyArticleSummaryEntity, Long> {
    /** UPSERT 逻辑：存在则更新，否则插入 */
    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO daily_article_summary(open_id, summary_date, article, mood_keywords, model, token_usage, created_at, updated_at)
        VALUES(:openId, :summaryDate, :article, :moodKeywords, :model, CAST(:tokenUsageJson AS jsonb), NOW(), NOW())
        ON CONFLICT (open_id, summary_date)
        DO UPDATE SET
            article = EXCLUDED.article,
            mood_keywords = EXCLUDED.mood_keywords,
            model = EXCLUDED.model,
            token_usage = EXCLUDED.token_usage,
            updated_at = NOW()
        """, nativeQuery = true)
    void upsertSummary(@Param("openId") String openId,
                       @Param("summaryDate") LocalDate summaryDate,
                       @Param("article") String article,
                       @Param("moodKeywords") String moodKeywords,
                       @Param("model") String model,
                       @Param("tokenUsageJson") String tokenUsageJson);

    /** 判断是否存在记录 */
    boolean exists(String openId, LocalDate summaryDate);
}