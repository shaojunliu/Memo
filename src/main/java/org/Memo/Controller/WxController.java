package org.Memo.Controller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
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

    // 和「微信公众平台 → 基本配置 → 服务器配置」里填的一致
    String wechatToken = "memo123";


    /** 微信URL校验：必须原样返回 echostr（纯文本） */
    @GetMapping
    public void verify(
            @RequestParam(required = false) String signature,
            @RequestParam(required = false) String timestamp,
            @RequestParam(required = false) String nonce,
            @RequestParam(required = false) String echostr,
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

            boolean pass = checkSignature(wechatToken, timestamp, nonce, signature);
            log.info("[WX VERIFY] ts={} nonce={} pass={}", timestamp, nonce, pass);

            resp.setContentType("text/plain;charset=UTF-8");
            if (pass) {
                // 必须原样返回 echostr（纯文本、无多余字符）
                resp.setStatus(200);
                resp.getWriter().write(echostr);
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
