package org.Memo.Controller;


import lombok.RequiredArgsConstructor;
import org.Memo.Service.DailySummarizeService;
import org.apache.catalina.valves.JsonAccessLogValve;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ops/summarize")
public class SummarizeOpsController {

    private final DailySummarizeService service;

    @PostMapping("/daily")
    public String daily(@RequestParam("date") String date,
                        @RequestParam(value = "tz", defaultValue = "Asia/Shanghai") String tz,
                        @RequestParam(value = "unionIds", required = false) List<String> unionIds) {
        LocalDate d = LocalDate.parse(date); // 例如 2025-10-10

        // 1) 未传 unionIds -> 走原有批量逻辑（当天全部用户，幂等）
        if (unionIds == null || unionIds.isEmpty()) {
            service.summarizeForDate(d, null, false);
            return "OK batch-all " + d;
        }

        service.summarizeForDate(d, unionIds, true);
        return "OK batch-manual " + d + " unionIds=" + unionIds;
    }

}
