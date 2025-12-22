package org.Memo.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.Login.LoginRequest;
import org.Memo.DTO.Login.LoginResponse;
import org.Memo.Entity.User;
import org.Memo.Repo.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import java.util.Objects;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

@Slf4j
@Service
public class WxAuthService {

    @Value("${wx.appid}")
    private String appId;

    @Value("${wx.secret}")
    private String secret;
    @Value("${wx.mock}")
    private boolean mock;

    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.ttl-seconds}")
    private long jwtTtlSeconds;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepo;

    public WxAuthService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public LoginResponse loginOrRegister(LoginRequest req, String ip) {
        // 调用wx code2session接口
        Code2SessionResp wx = mock ? mockResp(req.getCode()) : code2Session(req.getCode());
        log.info("code2SessionResp:{}", wx);
        if (wx.openid == null) {

            throw new RuntimeException("wx login failed: " + wx.errcode + " - " + wx.errmsg);
        }

        BeanWrapper bw = new BeanWrapperImpl(req);

        Double reqLat = readDouble(bw, "lastLoginLat", "lat", "latitude",
                "userPosition.latitude", "userPosition.lat");

        Double reqLng = readDouble(bw, "lastLoginLng", "lng", "longitude",
                "userPosition.longitude", "userPosition.lng");

        // 拿到全局唯一id openId 查找或存储用户信息
        User u = userRepo.findByUnionId(wx.unionid).orElseGet(() -> {
            User nu = new User();
            nu.setOpenId(wx.openid);
            nu.setUnionId(wx.unionid);
            nu.setNickname(req.getNickname());
            nu.setAvatarUrl(req.getAvatarUrl());
            if (reqLat != null) {
                nu.setLastLoginLat(reqLat);
            }
            if (reqLng != null) {
                nu.setLastLoginLng(reqLng);
            }
            Instant now = Instant.now();
            ZonedDateTime beijingTime = now.atZone(ZoneId.of("Asia/Shanghai"));
            nu.setCreatedAt(beijingTime.toInstant());
            return userRepo.save(nu);
        });
        if (StringUtils.isEmpty(u.getOpenId())) {
            u.setOpenId(wx.openid);
        }

        // 客户端可能在“用户资料变更 / 经纬度变更”时也会调用该接口：
        // 仅当入参不为空且与数据库不一致时，才更新对应字段，避免无意义写库。
        if (StringUtils.hasText(req.getNickname()) && !Objects.equals(req.getNickname(), u.getNickname())) {
            u.setNickname(req.getNickname());
        }
        if (StringUtils.hasText(req.getAvatarUrl()) && !Objects.equals(req.getAvatarUrl(), u.getAvatarUrl())) {
            u.setAvatarUrl(req.getAvatarUrl());
        }

        if (reqLat != null && !Objects.equals(reqLat, u.getLastLoginLat())) {
            u.setLastLoginLat(reqLat);
        }
        if (reqLng != null && !Objects.equals(reqLng, u.getLastLoginLng())) {
            u.setLastLoginLng(reqLng);
        }

        u.setLastLoginIp(ip);
        u.setLastLoginAt(Instant.now());
        userRepo.save(u);

        // 生成 JWT
        Instant now = Instant.now();
        String token = Jwts.builder()
                .setSubject(String.valueOf(u.getId()))
                .claim("openid", u.getOpenId())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(jwtTtlSeconds)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(u.getId());
        resp.setOpenId(u.getOpenId());
        resp.setExpiresIn(jwtTtlSeconds);
        return resp;
    }

    private Code2SessionResp code2Session(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + appId
                + "&secret=" + secret
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        log.info("Code2Session_url:{}",url);


        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url,  String.class);
            String body = resp.getBody();
            // 可选：调试日志
            log.info("code2Session raw: " + body + " | ct=" + resp.getHeaders().getContentType());
            return objectMapper.readValue(body, Code2SessionResp.class);
        } catch (Exception e) {
            throw new RuntimeException("code2Session failed: " + e);
        }
    }

    private Code2SessionResp mockResp(String code) {
        Code2SessionResp r = new Code2SessionResp();
        r.openid = "mock_" + code;
        r.sessionKey = "mock_session_key_" + code;
        r.unionid = null;
        return r;
    }

    private static Double readDouble(BeanWrapper bw, String... candidates) {
        for (String name : candidates) {
            try {
                if (bw.isReadableProperty(name)) {
                    Object v = bw.getPropertyValue(name);
                    if (v == null) {
                        continue;
                    }
                    if (v instanceof Number) {
                        return ((Number) v).doubleValue();
                    }
                    if (v instanceof String s && StringUtils.hasText(s)) {
                        try {
                            return Double.parseDouble(s);
                        } catch (NumberFormatException ignore) {
                            // ignore
                        }
                    }
                }
            } catch (Exception ignore) {
                // ignore
            }
        }
        return null;
    }

    public static class Code2SessionResp {
        @JsonProperty("openid") public String openid;
        @JsonProperty("session_key") public String sessionKey;
        @JsonProperty("unionid") public String unionid;
        @JsonProperty("errcode") public Integer errcode;
        @JsonProperty("errmsg") public String errmsg;
    }

}
