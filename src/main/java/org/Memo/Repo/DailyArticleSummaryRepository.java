package org.Memo.Repo;

import org.Memo.Entity.DailyArticleSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface DailyArticleSummaryRepository extends JpaRepository<DailyArticleSummaryEntity, Long> {
    /** UPSERT 逻辑：存在则更新，否则插入 */
    @Modifying
    @Transactional
    @Query(value = """
    INSERT INTO daily_article_summary(
        open_id, summary_date, article, mood_keywords,action_keywords,memory_point,analyze_result,article_title, model, token_usage, created_at, updated_at
    )
    VALUES(:openId, :summaryDate, :article, :moodKeywords, :actionKeywords,:memoryPoint,:analyzeResult , :articleTitle, :model,
           CAST(COALESCE(NULLIF(:tokenUsageJson, ''), '{}') AS jsonb),
           NOW(), NOW())
    ON CONFLICT (open_id, summary_date)
    DO UPDATE SET
        article       = EXCLUDED.article,
        mood_keywords = EXCLUDED.mood_keywords,
        action_keywords = EXCLUDED.action_keywords,
        memory_point = EXCLUDED.memory_point,
        analyze_result = EXCLUDED.analyze_result,
        article_title = EXCLUDED.article_title,
        model         = EXCLUDED.model,
        token_usage   = EXCLUDED.token_usage,
        updated_at    = NOW()
    """, nativeQuery = true)
    void upsertSummary(@Param("openId") String openId,
                       @Param("summaryDate") LocalDate summaryDate,
                       @Param("article") String article,
                       @Param("moodKeywords") String moodKeywords,
                       @Param("actionKeywords") String actionKeywords,
                       @Param("memoryPoint") String memoryPoint,
                       @Param("analyzeResult") String analyzeResult,
                       @Param("articleTitle") String articleTitle,
                       @Param("model") String model,
                       @Param("tokenUsageJson") String tokenUsageJson);

    /** 判断是否存在记录 */
    boolean existsByOpenIdAndSummaryDate(String openId, LocalDate summaryDate);

    // ==============================
    // 查询：按 openId 全量（倒序）
    // ==============================
    @Query("""
        SELECT e FROM DailyArticleSummaryEntity e
        WHERE e.openId = :openId
        ORDER BY e.summaryDate DESC
        """)
    List<DailyArticleSummaryEntity> findByOpenIdOrderBySummaryDateDesc(
            @Param("openId") String openId
    );

    // ==============================
    // 查询：按 openId + 日期范围（含端点）
    // ==============================
    @Query("""
        SELECT e FROM DailyArticleSummaryEntity e
        WHERE e.openId = :openId
          AND e.summaryDate BETWEEN :start AND :end
        ORDER BY e.summaryDate DESC
        """)
    List<DailyArticleSummaryEntity> findByOpenIdAndSummaryDateBetweenOrderBySummaryDateDesc(
            @Param("openId") String openId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    // ==============================
    // 分页版本（可选）
    // ==============================
    @Query("""
        SELECT e FROM DailyArticleSummaryEntity e
        WHERE e.openId = :openId
        ORDER BY e.summaryDate DESC
        """)
    Page<DailyArticleSummaryEntity> findByOpenIdOrderBySummaryDateDesc(
            @Param("openId") String openId,
            Pageable pageable
    );

    @Query("""
        SELECT e FROM DailyArticleSummaryEntity e
        WHERE e.openId = :openId
          AND e.summaryDate BETWEEN :start AND :end
        ORDER BY e.summaryDate DESC
        """)
    Page<DailyArticleSummaryEntity> findByOpenIdAndSummaryDateBetweenOrderBySummaryDateDesc(
            @Param("openId") String openId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            Pageable pageable
    );

    // ==============================
    // 单天查询（可选）
    // ==============================
    @Query("""
        SELECT e FROM DailyArticleSummaryEntity e
        WHERE e.openId = :openId
          AND e.summaryDate = :day
        ORDER BY e.summaryDate DESC
        """)
    List<DailyArticleSummaryEntity> findByOpenIdAndSummaryDate(
            @Param("openId") String openId,
            @Param("day") LocalDate day
    );

    @Query("""
        SELECT COUNT(e) FROM DailyArticleSummaryEntity e
        WHERE e.openId = :openId
    """)
    long countByOpenId(@Param("openId") String openId);

    @Query("""
        SELECT e FROM DailyArticleSummaryEntity e
        WHERE e.id = :id
    """)
    Optional<DailyArticleSummaryEntity> findOneById(@Param("id") Long id);

}