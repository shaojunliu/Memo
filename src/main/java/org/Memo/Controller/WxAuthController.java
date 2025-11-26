package org.Memo.Controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.Login.LoginRequest;
import org.Memo.DTO.Login.LoginResponse;
import org.Memo.Service.WxAuthService;
import org.Memo.DTO.ApiResponse;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class WxAuthController {
    private final WxAuthService wxAuthService;

    public WxAuthController(WxAuthService wxAuthService) {
        this.wxAuthService = wxAuthService;
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

}