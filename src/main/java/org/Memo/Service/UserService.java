package org.Memo.Service;

import lombok.extern.slf4j.Slf4j;
import org.Memo.Entity.User;
import org.Memo.Repo.WxRepository;
import org.Memo.Repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final WxRepository wxRepository;
    public UserService(UserRepository userRepo, WxRepository wxRepo) {
        this.userRepository = userRepo;
        this.wxRepository = wxRepo;
    }

    /** 对外统一方法：根据服务号 openid 找到 unionid（优先用已有的 union_id 字段） */
    public String getUnionIdByOaOpenId(String oaOpenid) {
        // Step1: 按 oa_openid 查 已有unionid直接返回
        User user = userRepository.findByOaOpenid(oaOpenid).orElse(null);
        if (user != null && StringUtils.hasText(user.getUnionId())) {
            return user.getUnionId();
        }

        // Step2: 获取服务号token
        String accessToken = wxRepository.getOfficialAccessToken();

        // Step3 ：根据服务号 openid 调公众号接口拿 unionid
        String unionId = wxRepository.getUnionIdByOaOpenid(oaOpenid, accessToken);
        if (!StringUtils.hasText(unionId)) {
            log.error("resolveUnionIdFromOaOpenid: unionid is null, fallback to oa_openid={}", oaOpenid);
            return oaOpenid;
        }

        // Step4: 看unionid 在库里是否已有对应用户（小程序那边登录过）
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
