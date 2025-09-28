package org.Memo.Entity;

import jakarta.persistence.*;      // ★ Spring Boot 3 必须是 jakarta 包
import lombok.Data;

import java.time.Instant;

@Entity
@Data
@Table(name = "chatRecord")// ★ 避开 user 这样的保留字
public class ChatRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="open_id", unique = true, nullable = false)
    private String openId;

    private String userMessage;
    private String agentReply;
    private Instant createdAt;

    public ChatRecord() {}
}
