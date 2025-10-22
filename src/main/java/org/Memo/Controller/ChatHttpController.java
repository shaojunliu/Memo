package org.Memo.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.Memo.DTO.Chat.ChatRequest;
import org.Memo.DTO.Chat.ChatResponse;
import org.Memo.DTO.ApiResponse;
import org.Memo.Service.ChatRecordService;
import org.Memo.Service.OkHttpAgentClient;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatHttpController {

    private final ChatRecordService recordService;
    private final OkHttpAgentClient agentClient;

    /**
     * POST /api/chat
     * body: { "userId": "openid_xxx", "message": "你好" }
     * return: ApiResponse<ChatResponse>
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest req) {

        if (req == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail(400, "request body is required"));
        }
        if (!StringUtils.hasText(req.getUserId())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail(400, "userId(openid) is required"));
        }
        if (!StringUtils.hasText(req.getMessage())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail(400, "message is required"));
        }

        String openid = req.getUserId().trim();
        String message = req.getMessage().trim();
        String traceId = UUID.randomUUID().toString();

        ChatResponse chatResp = new ChatResponse();
        chatResp.setTraceId(traceId);

        try {
            // 串行执行，保证相同 openid 的对话顺序一致
            String reply = CompletableFuture.supplyAsync(() -> {
                Instant now = Instant.now();
                var rec = recordService.createSession(openid, now);
                UUID sessionId = rec.getSessionId();

                // 1. 保存用户消息
                recordService.append(sessionId, "user", message, now);

                // 2. 调用 Agent
                String r;
                try {
                    r = agentClient.chat(openid, message);
                    if (r == null) r = "";
                } catch (Exception e) {
                    log.error("agent error", e);
                    r = "（服务异常，请稍后再试）";
                }

                // 3. 保存回复消息
                recordService.append(sessionId, "assistant", r, Instant.now());
                return r;
            }, recordService.executorFor(openid)).join();

            chatResp.setReply(reply);
            return ResponseEntity.ok(ApiResponse.ok(chatResp));

        } catch (Exception e) {
            log.error("HTTP chat error", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.fail(500, "internal error"));
        }
    }
}
