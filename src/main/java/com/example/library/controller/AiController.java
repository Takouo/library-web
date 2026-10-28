package com.example.library.controller;

import com.example.library.dto.ApiResponse;
import com.example.library.service.AiService;
import com.example.library.service.SimpleAiService;
import com.example.library.service.SpringAgentService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "*") // 允许所有来源跨域
@RestController
@RequestMapping("/ai")
public class AiController {

    private final AiService aiService;
    private final SimpleAiService simpleAiService;
    private final SpringAgentService springAgentService;

    // 构造器注入所有的 Service
    public AiController(AiService aiService,
                        SimpleAiService simpleAiService,
                        SpringAgentService springAgentService) {
        this.aiService = aiService;
        this.simpleAiService = simpleAiService;
        this.springAgentService = springAgentService;
    }

    // ==================== 1. 手写底层 HTTP 版本 ====================

    @PostMapping("/chat")
    public ApiResponse<String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isBlank()) {
            return ApiResponse.error(400, "message 不能为空");
        }
        return ApiResponse.success(aiService.chat(message));
    }

    @PostMapping("/recommend")
    public ApiResponse<String> recommend(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        if (question == null || question.isBlank()) {
            return ApiResponse.error(400, "question 不能为空");
        }
        return ApiResponse.success(aiService.recommend(question));
    }

    @PostMapping("/agent")
    public ApiResponse<String> agent(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isBlank()) {
            return ApiResponse.error(400, "message 不能为空");
        }
        return ApiResponse.success(aiService.chatWithTools(message));
    }

    @PostMapping("/react")
    public ApiResponse<String> react(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isBlank()) {
            return ApiResponse.error(400, "message 不能为空");
        }
        return ApiResponse.success(aiService.reactAgent(message));
    }


    // ==================== 2. Spring AI 框架版本 ====================

    @PostMapping("/simple")
    public ApiResponse<String> simple(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isBlank()) {
            return ApiResponse.error(400, "message 不能为空");
        }
        return ApiResponse.success(simpleAiService.chat(message));
    }

    @PostMapping("/spring-agent")
    public ApiResponse<String> springAgent(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isBlank()) {
            return ApiResponse.error(400, "message 不能为空");
        }
        return ApiResponse.success(springAgentService.agent(message));
    }
}