package org.Memo.DTO.Login;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private Long   userId;
    private String openId;
    private Long   expiresIn; // 秒
}
