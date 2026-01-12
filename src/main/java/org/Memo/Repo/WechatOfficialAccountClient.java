package org.Memo.Repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WechatOfficialAccountClient {

    private static final String SEND_CUSTOM_MESSAGE_URL = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=%s";
    // 小程序卡片缩略图（永久素材 media_id）
    private static final String THUMB_MEDIA_ID = "B3O_uGCaPAvNWE1hxoLb5WSH_ud7aYe7F6JDXxVtBPW87kSa3SZbsSSaqXmYL0zN";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送「客服消息」：文本 + 小程序卡片跳转
     *
     * @param oaOpenId   服务号 openId
     * @param content    展示文本
     * @param miniAppId  小程序 appid
     * @param pagePath   小程序页面路径（pages/xxx/index?date=xxxx）
     */
    public void sendTextWithMiniProgram(String accessToken, String oaOpenId, String content, String miniAppId, String pagePath) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("touser", oaOpenId);
            payload.put("msgtype", "miniprogrampage");

            Map<String, Object> miniProgramPage = new HashMap<>();
            miniProgramPage.put("title", content);
            miniProgramPage.put("appid", miniAppId);
            miniProgramPage.put("pagepath", pagePath);
            miniProgramPage.put("thumb_media_id", THUMB_MEDIA_ID);
            payload.put("miniprogrampage", miniProgramPage);

            String url = String.format(SEND_CUSTOM_MESSAGE_URL, accessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = objectMapper.writeValueAsString(payload);
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            String resp = restTemplate.postForObject(url, request, String.class);
            log.info("wechat custom msg sent, oaOpenId={}, resp={}", oaOpenId, resp);
        } catch (Exception e) {
            log.error("wechat custom msg send fail, openId={}", oaOpenId, e);
            throw new RuntimeException(e);
        }
    }
}
