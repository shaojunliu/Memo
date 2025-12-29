package org.Memo.Service;

import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.UserInfoRequest;
import org.Memo.Entity.User;
import org.Memo.Repo.WxRepository;
import org.Memo.Repo.UserRepository;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final WxRepository wxRepository;
    public UserService(UserRepository userRepo, WxRepository wxRepo) {
        this.userRepository = userRepo;
        this.wxRepository = wxRepo;
    }

    /** 对外统一方法：根据服务号 oaOpenid 找到 unionid（优先用已有的 union_id 字段） */
    public User getUserByOaOpenId(String oaOpenid) {
        // Step1: 按 oa_openid 查 已有unionid直接返回
        User user = userRepository.findByOaOpenid(oaOpenid).orElse(null);
        if (user != null && StringUtils.hasText(user.getUnionId())) {
            return user;
        }

        // Step2: 获取服务号token
        String accessToken = wxRepository.getOfficialAccessToken();

        // Step3 ：根据服务号 openid 调公众号接口拿 unionid
        String unionId = wxRepository.getUnionIdByOaOpenid(oaOpenid, accessToken);
        if (!StringUtils.hasText(unionId)) {
            log.error("resolveUnionIdFromOaOpenid: unionid is null, fallback to oa_openid={}", oaOpenid);
            return null;
        }

        // Step4: 看unionid 在库里是否已有对应用户（小程序那边登录过）
        User unionUser = userRepository.findByUnionId(unionId).orElse(null);
        if (unionUser != null) {
            // 小程序用户已存在，只需要补上 oa_openid 即可
            if (!oaOpenid.equals(unionUser.getOaOpenId())) {
                unionUser.setOaOpenId(oaOpenid);
                userRepository.save(unionUser);
            }
            return unionUser;
        }

        // Step4: 既没有 oa_openid 记录，也没有 unionid 记录，新建一个用户
        if (user == null) {
            user = new User();
            user.setOaOpenId(oaOpenid);
        }
        user.setUnionId(unionId);
        userRepository.save(user);

        return user;
    }


    public User modifyUserInfo(UserInfoRequest req, User user) {
        BeanWrapper bw = new BeanWrapperImpl(req);

        Double reqLat = readDouble(bw, "lastLoginLat", "lat", "latitude",
                "userPosition.latitude", "userPosition.lat");

        Double reqLng = readDouble(bw, "lastLoginLng", "lng", "longitude",
                "userPosition.longitude", "userPosition.lng");
        if (StringUtils.hasText(req.getNickname()) && !Objects.equals(req.getNickname(), user.getNickname())) {
            user.setNickname(req.getNickname());
        }
        if (StringUtils.hasText(req.getAvatarUrl()) && !Objects.equals(req.getAvatarUrl(), user.getAvatarUrl())) {
            user.setAvatarUrl(req.getAvatarUrl());
        }

        if (reqLat != null && !Objects.equals(reqLat, user.getLastLoginLat())) {
            user.setLastLoginLat(reqLat);
        }
        if (reqLng != null && !Objects.equals(reqLng, user.getLastLoginLng())) {
            user.setLastLoginLng(reqLng);
        }

        return userRepository.save(user);
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

}
