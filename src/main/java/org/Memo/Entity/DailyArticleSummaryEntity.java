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

    @Lob
    @Column(name = "article", nullable = false)
    private String article;

    @Column(name = "mood_keywords", nullable = false)
    private String moodKeywords;

    @Column(name = "model")
    private String model;

    @Column(name = "token_usage", columnDefinition = "jsonb")
    private String tokenUsageJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
