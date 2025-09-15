package org.Memo.Service;

import org.Memo.DTO.*;
import org.Memo.Entity.User;
import org.Memo.Repo.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

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
    private final UserRepository userRepo;

    public WxAuthService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public LoginResponse loginOrRegister(LoginRequest req, String ip) {
        Code2SessionResp wx = mock ? mockResp(req.getCode()) : code2Session(req.getCode());
        if (wx.openid == null) {
            throw new RuntimeException("wx login failed: " + wx.errcode + " - " + wx.errmsg);
        }

        User u = userRepo.findByOpenId(wx.openid).orElseGet(() -> {
            User nu = new User();
            nu.setOpenId(wx.openid);
            nu.setUnionId(wx.unionid);
            nu.setNickname(req.getNickname());
            nu.setAvatarUrl(req.getAvatarUrl());
            nu.setCreatedAt(Instant.now());
            return userRepo.save(nu);
        });

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

        return restTemplate.getForObject(url, Code2SessionResp.class);
    }

    private Code2SessionResp mockResp(String code) {
        Code2SessionResp r = new Code2SessionResp();
        r.openid = "mock_" + code;
        r.sessionKey = "mock_session_key_" + code;
        r.unionid = null;
        return r;
    }

    public static class Code2SessionResp {
        @JsonProperty("openid") public String openid;
        @JsonProperty("session_key") public String sessionKey;
        @JsonProperty("unionid") public String unionid;
        @JsonProperty("errcode") public Integer errcode;
        @JsonProperty("errmsg") public String errmsg;
    }

}
