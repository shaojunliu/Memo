package org.Memo.DTO;

import lombok.Data;

@Data
public class UserInfoRequest {
    private String openId;//小程序openid
    private String nickname;       // 可选：前端获取到的用户昵称
    private String avatarUrl;      // 可选：头像
    private String encryptedData;
    private String ip;
    private String phoneCode;
    private Position userPosition;
}
