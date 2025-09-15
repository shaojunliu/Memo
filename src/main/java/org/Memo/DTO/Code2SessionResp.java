package org.Memo.DTO;

import lombok.Data;

@Data
public class Code2SessionResp {
    private String openid;
    private String session_key;
    private String unionid;
    private Integer errcode;
    private String errmsg;
}
