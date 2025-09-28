package org.Memo.Controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.Login.LoginRequest;
import org.Memo.DTO.Login.LoginResponse;
import org.Memo.Service.WxAuthService;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class WxAuthController {


    private final WxAuthService wxAuthService;

    public WxAuthController(WxAuthService wxAuthService) {
        this.wxAuthService = wxAuthService;
    }

    @PostMapping("/wx/login")
    public LoginResponse wxLogin(@RequestBody LoginRequest req,
                                 HttpServletRequest httpReq) {
        log.info("LoginRequest:{}", req);
        String ip = httpReq.getRemoteAddr();
        LoginResponse loginResponse = wxAuthService.loginOrRegister(req, ip);
        log.info("loginResponse:{}", loginResponse);
        return loginResponse;
    }

    // 示例：受保护接口，需携带 Authorization: Bearer <token>
    @GetMapping("/me")
    public String me(@RequestAttribute(name = "uid", required = false) Long uid) {
        return uid == null ? "anonymous" : ("uid=" + uid);
    }


}
