package org.Memo.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.Memo.Entity.DailyArticleSummaryEntity;
import org.Memo.Entity.User;
import org.Memo.Repo.DailyArticleSummaryRepository;
import org.Memo.Repo.UserRepository;
import org.Memo.Repo.WechatOfficialAccountClient;
import org.Memo.Repo.WxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushSummaryService {
    private final UserRepository userRepo;
    private final DailyArticleSummaryRepository dailySummaryRepo;
    private final WechatOfficialAccountClient wechatOfficialAccountClient;
    private final WxRepository wxRepository;

    @Value("${wechat.miniapp.appid:wx24a59aa9e1797a8d}")
    private String miniAppId;

    @Value("${wechat.miniapp.dailySummaryPagePath:pages/detail/detail}")
    private String dailySummaryPagePath;

    public void sendDailySummary(String unionId, LocalDate d) {
        if (unionId == null || unionId.isBlank()) {
            log.warn("sendDailySummary skip: unionId blank, date={}", d);
            return;
        }
        if (d == null) {
            log.warn("sendDailySummary skip: date null, unionId={}", unionId);
            return;
        }

        // 1) 找到服务号 openId（这里假设 User 实体上有 getMpOpenId()，你可按实际字段名调整）
        User user = userRepo.findByUnionId(unionId).orElse(null);
        if (user == null) {
            log.warn("sendDailySummary skip: user not found, unionId={}, date={}", unionId, d);
            return;
        }
        String oaOpenId = user.getOaOpenId();
        if (oaOpenId == null || oaOpenId.isBlank()) {
            log.warn("sendDailySummary skip: mpOpenId blank, unionId={}, date={}", unionId, d);
            return;
        }

        // 2) 取当日总结内容（用于拼一句话；若你只想固定一句话也可以不查库）
        DailyArticleSummaryEntity summary = dailySummaryRepo.findByOpenIdAndSummaryDate(unionId, d).stream().findFirst().orElse(null);
        String title = (summary != null && summary.getArticleTitle() != null && !summary.getArticleTitle().isBlank())
                ? summary.getArticleTitle()
                : "今日回响已生成";

        // 3) 组织小程序落地页（你可以按你的页面结构改 pagePath）
        String pagePath = dailySummaryPagePath;
        pagePath = pagePath + "?articleId=" + summary.getId() + "&summaryType=Daily";

        // 4) 发送服务号消息（这里走“客服消息/模板消息”均可，由 client 内部实现）
        String content = "📝 " + "今日回响已生成!" + "点击进入查看";
        if (miniAppId == null || miniAppId.isBlank()) {
            log.warn("sendDailySummary skip: miniAppId not configured, unionId={}, date={}, oaOpenId={}", unionId, d, oaOpenId);
            return;
        }

        try {
            String accessToken = wxRepository.getOfficialAccessToken();
            //客服消息
            //wechatOfficialAccountClient.sendTextWithMiniProgram(accessToken, oaOpenId, content, miniAppId, pagePath);

            // 组装模板消息 data（字段名必须与模板完全一致）
            HashMap<String, Map<String, String>> data = new HashMap<>();
            // thing1：记录名称
            data.put("thing1", new HashMap<>() {{put("value", title);}});
            // time2：提醒时间
            data.put("time2", new HashMap<>() {{put("value", d.toString());}});
            wechatOfficialAccountClient.sendMiniProgramSubscribeMessage(accessToken, oaOpenId, "OKd5nPgdYWC_VbqcIADb-luwHpvbV4suELCLBI7gyag", miniAppId, pagePath,data);


            log.info("sendDailySummary success: unionId={}, date={}, oaOpenId={}", unionId, d, oaOpenId);
        } catch (Exception e) {
            log.error("sendDailySummary fail: unionId={}, date={}, oaOpenId={}", unionId, d, oaOpenId, e);
        }
    }

}
