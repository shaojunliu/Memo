package org.Memo.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.SummaryModel;
import org.Memo.Entity.User;
import org.Memo.Repo.ChatRecordRepository;
import org.Memo.Service.ChatRecordService;
import org.Memo.Service.DailySummaryService;
import org.Memo.Service.OkHttpAgentClient;
import org.Memo.Service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/wx")
@RequiredArgsConstructor
@Slf4j
public class WxChatController {

    private final ChatRecordService recordService;
    private final OkHttpAgentClient agentClient;
    private final UserService userService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatRecordService chatRecordService;
    private final DailySummaryService dailySummaryService;

    // 使用服务号的 appid / secret（不要用小程序的）
    @Value("${wx.oa.appid}")
    private String oaAppId;

    @Value("${wx.oa.secret}")
    private String oaSecret;

    @Value("${app.tz}")
    private String tz;

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
    // ======================= 2. 消息接收（POST /wx） =======================

    /**
     * 微信消息推送入口（明文模式）
     *
     * 这里不做同步长耗时回复：
     *  - 5 秒内快速返回 "success"
     *  - 后台异步调 Agent，处理完成后通过「客服消息接口」推给用户
     */
    @PostMapping(produces = "application/xml;charset=UTF-8")
    public String receive(@RequestBody String xml) {
        log.info("[WX POST] {}", xml);

        String fromUser = cdata(xml, "FromUserName"); // 用户 openid（服务号 openid
        String msgType  = cdata(xml, "MsgType");
        String content  = cdata(xml, "Content");

        // 这里只处理文本消息，其它类型直接忽略
        if (!"text".equalsIgnoreCase(msgType)) {
            log.info("[WX POST] unsupported MsgType={}, ignore", msgType);
            return "success";
        }
        User user = userService.getUserByOaOpenId(fromUser);
        if (user == null) {
            log.error("userService getUserByOaOpenId: user is null, fallback to oa_openid={}", fromUser);
            return "fail";
        }
        String unionId = user.getUnionId();
        String traceId = UUID.randomUUID().toString();
        HashMap<String, String> args = new HashMap<>();
        args.put("lng", String.valueOf(user.getLastLoginLng()));
        args.put("lat", String.valueOf(user.getLastLoginLat()));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日HH时mm分");
        String currentTime = LocalDateTime.now(ZoneId.systemDefault()).format(formatter);
        args.put("currentTime", currentTime);
        args.put("traceId", traceId);

        log.info("[WX POST] traceId={}, unionId={}, content={}", unionId, fromUser, content);

        try {
            // 使用你已有的 per-user executor，保证同一用户消息顺序
            Executor executor = recordService.executorFor(unionId);
            executor.execute(() -> {
                try {
                    Instant now = Instant.now();
                    var rec = recordService.createSession(unionId, now);
                    var sessionId = rec.getSessionId();
                    List<ChatRecordService.MsgItem> preChat = chatRecordService.getPreChatByUnionIdAndDay(unionId);

                    // 1. 记录用户消息
                    recordService.append(sessionId, "user", content, now);

                    // 2. 调用 Agent 获取回复
                    String reply = getReply(unionId, content, traceId, args, preChat);

                    // 3. 写入助手消息
                    recordService.append(sessionId, "assistant", reply, Instant.now());

                    // 4. 通过「客服消息接口」异步推送给用户
                    sendKfText(fromUser, reply);
                } catch (Exception ex) {
                    log.error("[WX] async handle error, traceId={}", traceId, ex);
                }
            });
        } catch (Exception e) {
            log.error("[WX] submit async task error, traceId={}", traceId, e);
        }
        return "success";
    }

    private String getReply(String unionId, String content, String traceId, HashMap<String, String> args,List<ChatRecordService.MsgItem> preChat) {
        String reply;
        try {
            List<SummaryModel> preSummary = dailySummaryService.getPreSummaryByUnionId(unionId);
            String raw = agentClient.chat(unionId, content,preChat,preSummary, args);
            // 尝试解析 JSON {"reply":"xxx"}
            try {
                var node = objectMapper.readTree(raw);
                if (node.has("reply")) {
                    reply = node.get("reply").asText();
                } else {
                    reply = raw;
                }
            } catch (Exception jsonEx) {
                // 不是 JSON 就直接用原始内容
                reply = raw;
            }
        } catch (Exception e) {
            log.error("[WX] agent error, traceId={}", traceId, e);
            reply = "（服务异常，请稍后再试）";
        }
        return reply;
    }


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


    /** 获取公众号 access_token（带简单内存缓存） */
    private String getOfficialAccessToken() {

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

    // ======================= 5. 客服消息推送 =======================

    /**
     * 通过「客服消息接口」发送文本消息给用户
     */
    private void sendKfText(String openid, String content) {
        String accessToken = getOfficialAccessToken();
        String urlStr = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=" + accessToken;

        try {
            // 1. 构造 JSON body，与 curl 保持一致
            Map<String, Object> payload = new HashMap<>();
            payload.put("touser", openid);
            payload.put("msgtype", "text");
            Map<String, String> text = new HashMap<>();
            text.put("content", content);
            payload.put("text", text);

            // 2. 构造 headers，尽量贴近 curl
            String jsonPayload = objectMapper.writeValueAsString(payload);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("User-Agent", "curl/8.5.0");

            byte[] out = jsonPayload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(out.length);
            conn.connect();

            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (is != null) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                is.close();
            }
            String body = baos.toString(StandardCharsets.UTF_8);

            log.info("[WX KF][HTTP] status={}, body={}", status, body);

        } catch (Exception e) {
            log.error("[WX KF] sendKfText exception, openid={}", openid, e);
        }
    }
}