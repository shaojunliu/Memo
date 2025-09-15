package org.Memo.Entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="open_id", unique = true, nullable = false)
    private String openId;

    private String unionId;
    private String nickname;
    private String avatarUrl;

    private Instant createdAt;
    private Instant lastLoginAt;
    private String lastLoginIp;
}
