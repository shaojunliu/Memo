package org.Memo.Controller;


import lombok.RequiredArgsConstructor;
import org.Memo.Service.DailySummarizeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ops/summarize")
public class SummarizeOpsController {

    private final DailySummarizeService service;

    @PostMapping("/daily")
    public String daily(@RequestParam("date") String date,
                        @RequestParam(value = "tz", defaultValue = "Asia/Shanghai") String tz,
                        @RequestParam("unionId") String unionId) {
        LocalDate d = LocalDate.parse(date); // 例如 2025-10-10
        service.summarizeForDate(d,unionId);
        return "OK " + d;
    }

}
