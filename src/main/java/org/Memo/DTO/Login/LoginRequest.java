package org.Memo.DTO.Login;

import lombok.Data;

// 请求体：
@Data
public class LoginRequest {
    private String code;           // 必填：wx.login()返回 前端把 wx.login() 的 code 传上来
    private String nickname;       // 可选：前端获取到的用户昵称
    private String avatarUrl;      // 可选：头像
    private String encryptedData;
    private String ip;
    private String phoneCode;
}

