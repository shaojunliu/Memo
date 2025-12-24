package org.Memo.Controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.ApiResponse;
import org.Memo.DTO.Login.LoginRequest;
import org.Memo.DTO.Login.LoginResponse;
import org.Memo.DTO.Position;
import org.Memo.DTO.UserInfoRequest;
import org.Memo.DTO.UserInfoResponse;
import org.Memo.Entity.User;
import org.Memo.Repo.UserRepository;
import org.Memo.Service.UserService;
import org.Memo.Service.WxAuthService;
import org.aspectj.apache.bcel.classfile.Module;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {
    private UserRepository userRepository;
    private UserService userService;

    /**
     * 登录或注册接口
     * 请求体：
     * {
     *   "code": "...",
     *   "nickname": "...",
     *   "avatarUrl": "..."
     * }
     */
    @PostMapping("/modify")
    public ApiResponse<UserInfoResponse> modify(@RequestBody UserInfoRequest req,
                                                HttpServletRequest httpReq) {
        log.info("modify UserInfo: {}", req);
        try {
            User oriUser = userRepository.findByOpenId(req.getOpenId()).orElse(null);

            if (oriUser == null) {
                return ApiResponse.fail(500,"User not found");
            }

            UserInfoResponse  response = new UserInfoResponse();
            User user = userService.modifyUserInfo(req, oriUser);
            response.setOpenId(user.getOpenId());
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("wxLogin error", e);
            return ApiResponse.fail(500, "修改用户信息失败: " + e.getMessage());
        }
    }

}
