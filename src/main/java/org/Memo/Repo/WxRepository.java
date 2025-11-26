package org.Memo.Repo;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class WxRepository {
    // 使用服务号的 appid / secret（不要用小程序的）
    @Value("${wx.oa.appid}")
    private String oaAppId;

    @Value("${wx.oa.secret}")
    private String oaSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getOfficialAccessToken() {
        String url = "https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential"
                + "&appid=" + oaAppId
                + "&secret=" + oaSecret;

        log.info("Request OA access_token: {}", url);
        String resp = restTemplate.getForObject(url, String.class);

        try {
            var json = objectMapper.readTree(resp);
            if (json.has("errcode")) {
                throw new RuntimeException("getOfficialAccessToken error: " + resp);
            }
            return json.get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("parse access_token failed: " + resp, e);
        }
    }

    public String getUnionIdByOaOpenid(String oaOpenid, String accessToken) {
        String url = "https://api.weixin.qq.com/cgi-bin/user/info"
                + "?access_token=" + accessToken
                + "&openid=" + oaOpenid
                + "&lang=zh_CN";

        log.info("OA user info url = {}", url);
        String respStr = restTemplate.getForObject(url, String.class);
        log.info("OA user info resp = {}", respStr);

        try {
            var json = objectMapper.readTree(respStr);
            if (json.has("errcode")) {
                log.error("getUnionIdByOaOpenid error: {}", respStr);
                return null;
            }
            if (json.has("unionid")) {
                return json.get("unionid").asText();
            } else {
                log.warn("no unionid in OA user info, openid={}", oaOpenid);
                return null;
            }
        } catch (Exception e) {
            log.error("parse OA user info failed", e);
            return null;
        }
    }
}
