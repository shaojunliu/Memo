package org.Memo.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.Memo.Entity.User;
import org.Memo.Repo.UserRepository;
import org.Memo.Service.ChatRecordService;
import org.Memo.Service.OkHttpAgentClient;
import org.Memo.Service.WxAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/wx")
@RequiredArgsConstructor
@Slf4j
public class WxController {

    private final ChatRecordService recordService;
    private final OkHttpAgentClient agentClient;
    private final UserRepository userRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 使用服务号的 appid / secret（不要用小程序的）
    @Value("${wx.oa.appid}")
    private String oaAppId;

    @Value("${wx.oa.secret}")
    private String oaSecret;

    private String officialAccessToken = null;
    private long tokenExpireTs = 0;

    // 和公众号后台填写一致
    private static final String WECHAT_TOKEN = "memo123";

    /** 微信URL校验：必须原样返回 echostr（纯文本） */
    @GetMapping
    public void verify(
            @RequestParam(value = "signature", required = false) String signature,
            @RequestParam(value = "timestamp", required = false) String timestamp,
            @RequestParam(value = "nonce",     required = false) String nonce,
            @RequestParam(value = "echostr",   required = false) String echostr,
            HttpServletResponse resp) throws IOException {

        try {
            if (!StringUtils.hasText(signature) ||
                    !StringUtils.hasText(timestamp) ||
                    !StringUtils.hasText(nonce) ||
                    !StringUtils.hasText(echostr)) {

                log.warn("[WX VERIFY] missing params sig={} ts={} nonce={} echostr={}",
                        signature, timestamp, nonce, echostr);
                resp.setStatus(400);
                resp.setContentType("text/plain;charset=UTF-8");
                resp.getWriter().write("missing params");
                return;
            }

            boolean pass = checkSignature(WECHAT_TOKEN, timestamp, nonce, signature);
            log.info("[WX VERIFY] ts={} nonce={} pass={}", timestamp, nonce, pass);

            resp.setContentType("text/plain;charset=UTF-8");
            if (pass) {
                resp.setStatus(200);
                resp.getWriter().write(echostr);   // 原样返回
            } else {
                resp.setStatus(403);
                resp.getWriter().write("invalid signature");
            }
        } catch (Exception e) {
            log.error("[WX VERIFY] exception", e);
            resp.setStatus(500);
            resp.setContentType("text/plain;charset=UTF-8");
            resp.getWriter().write("server error");
        }
    }

    /** 微信消息推送入口（明文模式） */
    @PostMapping(produces = "application/xml;charset=UTF-8")
    public String receive(@RequestBody String xml) {
        log.info("[WX chat request] {}", xml);
        long start = System.currentTimeMillis();

        String toUser   = cdata(xml, "ToUserName");
        String fromUser = cdata(xml, "FromUserName"); // openid
        String msgType  = cdata(xml, "MsgType");
        String content  = cdata(xml, "Content");
        // 根据服务号 openid 拿 unionid（内部统一用 unionid）
        String unionid = resolveUnionIdFromOaOpenId(fromUser);

        // 只处理文本消息
        if (!"text".equalsIgnoreCase(msgType)) {
            return textReply(fromUser, toUser, "暂不支持该类型消息～");
        }

        String traceId = UUID.randomUUID().toString();
        try {
            String finalUnionid = unionid;
            String reply = CompletableFuture.supplyAsync(() -> {
                Instant now = Instant.now();
                var rec = recordService.createSession(finalUnionid, now);
                var sessionId = rec.getSessionId();

                recordService.append(sessionId, "user", content, now);

                String r;
                try {
                    r = agentClient.chat(finalUnionid, content);
                    if (r == null) r = "";
                } catch (Exception e) {
                    log.error("[WX] agent error", e);
                    r = "（服务异常，请稍后再试）";
                }

                recordService.append(sessionId, "assistant", r, Instant.now());
                return r;
            }, recordService.executorFor(finalUnionid)).join();

            long end = System.currentTimeMillis();
            log.info("WX chat cost:{}", (end - start));
            return textReply(fromUser, toUser, reply);
        } catch (Exception e) {
            log.error("[WX] handle error, traceId={}", traceId, e);
            return textReply(fromUser, toUser, "（系统繁忙，请稍后再试）");
        }
    }

    // ========= 工具方法 =========

    private boolean checkSignature(String token, String timestamp, String nonce, String signature) throws Exception {
        String[] arr = {token, timestamp, nonce};
        Arrays.sort(arr);
        String joined = String.join("", arr);
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] d = md.digest(joined.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString().equalsIgnoreCase(signature);
    }

    private static final Pattern CDATA =
            Pattern.compile("<%s><!\\[CDATA\\[(.*?)]\\]></%s>", Pattern.DOTALL);

    private String cdata(String xml, String tag) {
        Pattern p = Pattern.compile(String.format(CDATA.pattern(), tag, tag), Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1).trim() : "";
    }

    private String textReply(String toUserOpenId, String fromGhid, String text) {
        long now = System.currentTimeMillis() / 1000;
        return """
               <xml>
                 <ToUserName><![CDATA[%s]]></ToUserName>
                 <FromUserName><![CDATA[%s]]></FromUserName>
                 <CreateTime>%d</CreateTime>
                 <MsgType><![CDATA[text]]></MsgType>
                 <Content><![CDATA[%s]]></Content>
               </xml>
               """.formatted(toUserOpenId, fromGhid, now, text);
    }


    /** 获取公众号 access_token（带简单内存缓存） */
    private String getOfficialAccessToken() {
        long now = System.currentTimeMillis();
        if (officialAccessToken != null && now < tokenExpireTs) {
            return officialAccessToken;
        }

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
            officialAccessToken = json.get("access_token").asText();
            int expiresIn = json.get("expires_in").asInt();   // 通常 7200
            tokenExpireTs = now + (expiresIn - 300) * 1000L;  // 提前5分钟过期
            return officialAccessToken;
        } catch (Exception e) {
            throw new RuntimeException("parse access_token failed: " + resp, e);
        }
    }

    /** 根据服务号 openid 调公众号接口拿 unionid */
    private String getUnionIdByOaOpenid(String oaOpenid) {
        String accessToken = getOfficialAccessToken();

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

    /** 对外统一方法：根据服务号 openid 找到 unionid（优先用已有的 union_id 字段） */
    private String resolveUnionIdFromOaOpenId(String oaOpenid) {
        // Step1: 按 oa_openid 查
        User user = userRepository.findByOaOpenid(oaOpenid).orElse(null);
        if (user != null && StringUtils.hasText(user.getUnionId())) {
            return user.getUnionId();  // 已有 unionid，直接返回
        }

        // Step2: 去微信拿 unionid
        String unionId = getUnionIdByOaOpenid(oaOpenid);
        if (!StringUtils.hasText(unionId)) {
            // 极端情况：拿不到 unionid，就先用 oa_openid 顶一下（避免整个流程挂）
            log.warn("resolveUnionIdFromOaOpenid: unionid is null, fallback to oa_openid={}", oaOpenid);
            return oaOpenid;
        }

        // Step3: 看 unionid 在库里是否已有对应用户（小程序那边登录过）
        User unionUser = userRepository.findByUnionId(unionId).orElse(null);
        if (unionUser != null) {
            // 小程序用户已存在，只需要补上 oa_openid 即可
            if (!oaOpenid.equals(unionUser.getOaOpenId())) {
                unionUser.setOaOpenId(oaOpenid);
                userRepository.save(unionUser);
            }
            return unionId;
        }

        // Step4: 既没有 oa_openid 记录，也没有 unionid 记录，新建一个用户
        if (user == null) {
            user = new User();
            user.setOaOpenId(oaOpenid);
        }
        user.setUnionId(unionId);
        userRepository.save(user);

        return unionId;
    }
}