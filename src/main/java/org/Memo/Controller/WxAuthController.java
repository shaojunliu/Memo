package org.Memo.Controller;

import jakarta.servlet.http.HttpServletRequest;
import org.Memo.DTO.LoginRequest;
import org.Memo.DTO.LoginResponse;
import org.Memo.Service.WxAuthService;
import org.springframework.web.bind.annotation.*;

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
        String ip = httpReq.getRemoteAddr();
        return wxAuthService.loginOrRegister(req, ip);
    }

    // 示例：受保护接口，需携带 Authorization: Bearer <token>
    @GetMapping("/me")
    public String me(@RequestAttribute(name = "uid", required = false) Long uid) {
        return uid == null ? "anonymous" : ("uid=" + uid);
    }


}
