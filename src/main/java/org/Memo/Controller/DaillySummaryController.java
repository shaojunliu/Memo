package org.Memo.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.ApiResponse;
import org.Memo.DTO.DaillySummarysModel;
import org.Memo.Entity.User;
import org.Memo.Repo.UserRepository;
import org.Memo.Service.DailySummaryService;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DaillySummaryController {
    private final DailySummaryService service;
    private final UserRepository userRepository;

    /**
     * 示例：
     * GET /api/getDaillySummarys?openid=o123
     * GET /api/getDaillySummarys?openid=o123&searchStartDay=2025-09-01&searchEndDay=2025-09-30
     */
    @GetMapping("/getDailySummary")
    public ApiResponse<DaillySummarysModel> getDailySummary(
            @RequestParam("openid") String openid,
            @RequestParam(value = "searchStartDay", required = false) String searchStartDay,
            @RequestParam(value = "searchEndDay", required = false) String searchEndDay
    ) {
        if (openid == null || openid.isBlank()) {
            return ApiResponse.fail(400, "openid 不能为空");
        }
        try {
            User user = userRepository.findByOpenId(openid).orElse(new User());
            DaillySummarysModel body = service.getDailySummary(user.getUnionId(), searchStartDay, searchEndDay);
            return ApiResponse.ok(body);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResponse.fail(500, "查询失败");
        }
    }
}

/*
{
  "resStatus": "OK",
  "resCode": 200,
  "resBody": {
    "DaillySummarySize": "2",
    "summarys": [
      {
        "atricle": "今天主要完成了接口联调...",
        "moodskeywords": "专注, 放松, 期待"
      },
      {
        "atricle": "国庆前的准备进度...",
        "moodskeywords": "紧张, 期待, 放松"
      }
    ]
  }
}
* */