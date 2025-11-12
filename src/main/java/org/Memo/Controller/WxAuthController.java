package org.Memo.Controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.Login.LoginRequest;
import org.Memo.DTO.Login.LoginResponse;
import org.Memo.Service.ChatRecordService;
import org.Memo.Service.OkHttpAgentClient;
import org.Memo.Service.WxAuthService;
import org.Memo.DTO.ApiResponse;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.Memo.Service.ChatRecordService;
import org.Memo.Service.OkHttpAgentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class WxAuthController {


    private final WxAuthService wxAuthService;

    String wechatToken = "memo123";
    private final ChatRecordService recordService;
    private final OkHttpAgentClient agentClient;

    public WxAuthController(WxAuthService wxAuthService, ChatRecordService recordService, OkHttpAgentClient agentClient) {
        this.wxAuthService = wxAuthService;
        this.recordService = recordService;
        this.agentClient = agentClient;
    }

    /**
     * 登录或注册接口
     * 请求体：
     * {
     *   "code": "...",
     *   "nickname": "...",
     *   "avatarUrl": "..."
     * }
     */
    @PostMapping("/wx/login")
    public ApiResponse<LoginResponse> wxLogin(@RequestBody LoginRequest req,
                                              HttpServletRequest httpReq) {
        log.info("LoginRequest: {}", req);
        try {
            String ip = httpReq.getRemoteAddr();
            LoginResponse loginResponse = wxAuthService.loginOrRegister(req, ip);
            log.info("loginResponse: {}", loginResponse);
            return ApiResponse.ok(loginResponse);
        } catch (Exception e) {
            log.error("wxLogin error", e);
            return ApiResponse.fail(500, "登录失败: " + e.getMessage());
        }
    }

    /**
     * 示例：受保护接口（测试 JWT 解析）
     */
    @GetMapping("/me")
    public ApiResponse<String> me(@RequestAttribute(name = "uid", required = false) Long uid) {
        String msg = uid == null ? "anonymous" : ("uid=" + uid);
        return ApiResponse.ok(msg);
    }

    /** 微信URL校验：必须原样返回 echostr（纯文本） */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam(required = false) String signature,
            @RequestParam(required = false) String timestamp,
            @RequestParam(required = false) String nonce,
            @RequestParam(required = false) String echostr) {

        if (!StringUtils.hasText(signature) ||
                !StringUtils.hasText(timestamp) ||
                !StringUtils.hasText(nonce) ||
                !StringUtils.hasText(echostr)) {
            return ResponseEntity.badRequest().body("missing params");
        }

        boolean pass = checkSignature(wechatToken, timestamp, nonce, signature);
        log.info("[WX VERIFY] ts={}, nonce={}, sign={}, pass={}", timestamp, nonce, signature, pass);
        return pass ? ResponseEntity.ok(echostr)
                : ResponseEntity.status(403).body("invalid signature");
    }


    /** 微信消息推送入口（明文模式） */
    @PostMapping(produces = "application/xml;charset=UTF-8")
    public String receive(@RequestBody String xml) {
        log.info("[WX POST] {}", xml);

        String toUser   = cdata(xml, "ToUserName");
        String fromUser = cdata(xml, "FromUserName"); // openid
        String msgType  = cdata(xml, "MsgType");
        String content  = cdata(xml, "Content");

        // 只处理文本消息，其他类型先回个提示
        if (!"text".equalsIgnoreCase(msgType)) {
            return textReply(fromUser, toUser, "暂不支持该类型消息～");
        }

        // 复用你现有的 recordService/agentClient 逻辑（保持与 /api/chat 行为一致）
        String traceId = UUID.randomUUID().toString();
        try {
            String reply = CompletableFuture.supplyAsync(() -> {
                Instant now = Instant.now();
                var rec = recordService.createSession(fromUser, now);
                var sessionId = rec.getSessionId();

                recordService.append(sessionId, "user", content, now);

                String r;
                try {
                    r = agentClient.chat(fromUser, content);
                    if (r == null) r = "";
                } catch (Exception e) {
                    log.error("[WX] agent error", e);
                    r = "（服务异常，请稍后再试）";
                }

                recordService.append(sessionId, "assistant", r, Instant.now());
                return r;
            }, recordService.executorFor(fromUser)).join();

            return textReply(fromUser, toUser, reply);
        } catch (Exception e) {
            log.error("[WX] handle error, traceId={}", traceId, e);
            return textReply(fromUser, toUser, "（系统繁忙，请稍后再试）");
        }
    }

    // ========= 工具方法 =========

    private boolean checkSignature(String token, String timestamp, String nonce, String signature) {
        try {
            String[] arr = {token, timestamp, nonce};
            Arrays.sort(arr);
            String joined = String.join("", arr);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString().equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("checkSignature error", e);
            return false;
        }
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
        // 注意：微信回包里 To/From 是**对调**的
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


}