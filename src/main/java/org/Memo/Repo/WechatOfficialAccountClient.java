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
    // 方案 A：公众号模板消息（进入「服务通知」）
    private static final String SEND_TEMPLATE_MESSAGE_URL =
            "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=%s";

    // 方案 B：小程序订阅消息（进入「服务通知」）
    private static final String SEND_MINI_SUBSCRIBE_URL =
            "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=%s";
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

    /**
     * 方案 A：发送公众号模板消息（会进入「服务通知」）
     *
     * 前置条件：
     * 1) 公众号后台已配置模板，拿到 templateId
     * 2) 用户未取消关注
     *
     * @param accessToken  公众号 access_token
     * @param oaOpenId     服务号 openId
     * @param templateId   模板 ID（公众号后台获取）
     * @param miniAppId    小程序 appid（可选）
     * @param pagePath     小程序页面路径（可选）
     * @param data         模板 data（key -> {value, color?}）
     */
    public void sendTemplateMessage(String accessToken, String oaOpenId, String templateId, String miniAppId, String pagePath, Map<String, Map<String, String>> data) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("touser", oaOpenId);
            payload.put("template_id", templateId);

            if (miniAppId != null && !miniAppId.isBlank()
                    && pagePath != null && !pagePath.isBlank()) {
                Map<String, String> miniProgram = new HashMap<>();
                miniProgram.put("appid", miniAppId);
                miniProgram.put("pagepath", pagePath);
                payload.put("miniprogram", miniProgram);
            }

            payload.put("data", data);

            String url = String.format(WechatOfficialAccountClient.SEND_TEMPLATE_MESSAGE_URL, accessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = objectMapper.writeValueAsString(payload);
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            String resp = restTemplate.postForObject(url, request, String.class);
            log.info("wechat template msg sent, oaOpenId={}, resp={}", oaOpenId, resp);
        } catch (Exception e) {
            log.error("wechat template msg send fail, oaOpenId={}", oaOpenId, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 方案 B：发送小程序订阅消息（进入「服务通知」）
     *
     * 前置条件：
     * 1) 用户在小程序内已「订阅/允许」该模板
     * 2) 使用的是小程序 access_token（不是公众号）
     *
     * @param accessToken  小程序 access_token
     * @param openId       小程序 openId
     * @param templateId   订阅模板 ID（小程序后台）
     * @param pagePath     小程序页面路径
     * @param data         模板 data（key -> {value}）
     */
    public void sendMiniProgramSubscribeMessage(String accessToken, String openId, String templateId, String pagePath, Map<String, Map<String, String>> data) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("touser", openId);
            payload.put("template_id", templateId);
            payload.put("page", pagePath);
            payload.put("data", data);

            String url = String.format(WechatOfficialAccountClient.SEND_MINI_SUBSCRIBE_URL, accessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = objectMapper.writeValueAsString(payload);
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            String resp = restTemplate.postForObject(url, request, String.class);
            log.info("wechat mini subscribe msg sent, openId={}, resp={}", openId, resp);
        } catch (Exception e) {
            log.error("wechat mini subscribe msg send fail, openId={}", openId, e);
            throw new RuntimeException(e);
        }
    }
}
