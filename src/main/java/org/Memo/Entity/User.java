package org.Memo.Entity;

import jakarta.persistence.*;      // ★ Spring Boot 3 必须是 jakarta 包
import lombok.Data;

import java.time.Instant;

@Entity
@Data
@Table(name = "users")// ★ 避开 user 这样的保留字
public class User {               // ★ public 顶级类，非 final，非内部类
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="open_id", unique = true, nullable = false)
    private String openId;// 小程序 openid
    @Column(name = "oa_openid")   //显式声明对应数据库的列名
    private String oaOpenId;// 服务号 openid
    private String unionId;
    private String nickname;
    private String avatarUrl;
    private Instant createdAt;
    private Instant lastLoginAt;
    private String lastLoginIp;

    public User() {}              // ★ 必须有无参构造
}