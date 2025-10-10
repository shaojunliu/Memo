package org.Memo.Controller.Scheduler;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.Memo.Service.DailySummarizeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class SummarizeDailyScheduler {

    private final DailySummarizeService service;

    // 用配置项控制时区和 cron，方便改
    @Value("${app.tz:Asia/Shanghai}")
    private String tz;

    // 每天 00:05 触发，跑“昨天”的总结（更稳妥，避开跨天写入/延迟）
    @Scheduled(cron = "${app.summarize.cron-daily:0 5 0 * * ?}", zone = "${app.tz:Asia/Shanghai}")
    public void runDaily() {
        log.info("SummarizeDailyScheduler Begin");
        ZoneId zone = ZoneId.of(tz);
        LocalDate target = ZonedDateTime.now(zone).minusDays(1).toLocalDate();
        service.summarizeForDate(target);
    }

    // 如果你还需要“每周日 00:00 回顾上周 7 天”的任务，保留这个：
/*    @Scheduled(cron = "${app.summarize.cron-weekly:0 0 0 ? * SUN}", zone = "${app.tz:Asia/Shanghai}")
    public void runWeekly() {
        ZoneId zone = ZoneId.of(tz);
        LocalDate sunday = ZonedDateTime.now(zone).toLocalDate(); // 触发当天（周日）
        LocalDate start = sunday.minusDays(7); // 上周日
        for (int i = 0; i < 7; i++) {
            service.summarizeForDate(start.plusDays(i));
        }
    }*/

}
