package org.Memo.Controller;

import lombok.RequiredArgsConstructor;
import org.Memo.DTO.ApiResponse;
import org.Memo.DTO.RecordModel;
import org.Memo.Service.MyRecordsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MyCenterController {
    private final MyRecordsService service;

    @GetMapping("/getMyRecords")
    public ApiResponse<RecordModel> getMyRecords(@RequestParam("openid") String openid) {
        if (openid == null || openid.isBlank()) {
            return ApiResponse.fail(400, "openid 不能为空");
        }
        try {
            RecordModel body = service.getMyRecords(openid);
            return ApiResponse.ok(body);
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询失败");
        }
    }
}
