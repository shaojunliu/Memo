package org.Memo.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.Memo.DTO.ApiResponse;
import org.Memo.DTO.GetSummaryDetailReq;
import org.Memo.DTO.GetSummaryDetailRes;
import org.Memo.Service.SummaryDetailService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SummaryDetailController {


    private final SummaryDetailService service;

    /**
     * POST /api/getSummaryDetail
     * Body:
     * {
     *   "summaryType": "daily",
     *   "articleId": 41
     * }
     */
    @PostMapping("/getSummaryDetail")
    public ApiResponse<GetSummaryDetailRes> getSummaryDetail(@Valid @RequestBody GetSummaryDetailReq req) {
        try {
            GetSummaryDetailRes body = service.getDetail(req);
            return ApiResponse.ok(body);
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询失败");
        }
    }
}
