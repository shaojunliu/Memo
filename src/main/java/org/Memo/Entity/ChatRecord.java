package org.Memo.Entity;

import jakarta.persistence.*;      // ★ Spring Boot 3 必须是 jakarta 包
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.DynamicInsert;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "chat_record")
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert   // 让 null 字段不出现在 INSERT 语句里，从而使用 PG 默认值
@Builder
public class ChatRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "open_id", nullable = false, length = 128)
    private String openId;

    @Column(name = "session_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID sessionId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "message_count", nullable = false)
    private Integer messageCount;

    @Column(name = "last_ts")
    private Instant lastTs;

    @Version // 使用 JPA 乐观锁，对应 version 列
    @Column(name = "version", nullable = false)
    private Long version;

    // 用 String 映射 jsonb（简单直观）；需要时再换 JsonNode
    @Column(name = "msgs", columnDefinition = "jsonb")
    private String msgs;

}
