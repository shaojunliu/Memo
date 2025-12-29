package org.Memo.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_article_summary",
        uniqueConstraints = @UniqueConstraint(columnNames = {"open_id","summary_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyArticleSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "open_id", nullable = false)
    private String openId;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "article", nullable = false, columnDefinition = "text")
    private String article;
    @Column(name = "mood_keywords")
    private String moodKeywords;

    @Column(name = "article_title")
    private String articleTitle;

    @Column(name = "action_keywords")
    private String actionKeywords;

    @Column(name = "memory_point")
    private String memoryPoint;

    @Column(name = "analyze_result")
    private String analyzeResult;

    @Column(name = "model")
    private String model;

    @Column(name = "token_usage", columnDefinition = "jsonb")
    private String tokenUsageJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
