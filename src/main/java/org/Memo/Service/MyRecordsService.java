package org.Memo.Service;

import lombok.RequiredArgsConstructor;
import org.Memo.DTO.RecordModel;
import org.Memo.Repo.ChatRecordRepository;
import org.Memo.Repo.DailyArticleSummaryRepository;
import org.Memo.Repo.UserRepository;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class MyRecordsService {

    private final UserRepository userRepository;
    private final ChatRecordRepository chatRecordRepository;
    private final DailyArticleSummaryRepository dailyArticleSummaryRepository;
    private final UserService userService;


    public RecordModel getMyRecords(String openId) {
        // 1) 念念天数（users.created_at → 到系统当前日期的天数，含当天）
        int nianNianDays = userRepository.findCreatedAtByOpenId(openId)
                .map(createdAt -> {
                    // 你的库已存东八区，本地服务一般也按系统默认时区
                    LocalDate start = createdAt.atZone(ZoneId.systemDefault()).toLocalDate();
                    LocalDate today = LocalDate.now(ZoneId.systemDefault());
                    long days = ChronoUnit.DAYS.between(start, today) + 1; // 含当天
                    return (int) Math.max(days, 1);
                })
                .orElse(0);

        // 2) 不忘条数（chat_record 表该 openid 的记录数）
        String unionId = userService.getUnionIdByOaOpenId(openId);
        long buWangCount = chatRecordRepository.countByOpenId(unionId);

        // 3) 回响篇数（summary/daily_article_summary 表该 openid 的条数）
        long huiXiangCount = dailyArticleSummaryRepository.countByOpenId(unionId);

        // 4) 每日回响时间（写死每天零点）
        return RecordModel.builder()
                .creatDays(nianNianDays)
                .recordCount((int) buWangCount)
                .summaryCount((int) huiXiangCount)
                .dailySummaryTime("00:05")
                .build();
    }
}
