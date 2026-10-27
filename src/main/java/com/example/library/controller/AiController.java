package com.example.library.controller;

import com.example.library.dto.ApiResponse;
import com.example.library.service.AiService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

//    @PostMapping("/chat")
@PostMapping("/recommend")
public ApiResponse<String> recommend(@RequestBody Map<String, String> request) {
    // 注意这里必须是 "question"，和你 Postman 里发的一致
    String question = request.get("question");

    if (question == null || question.isBlank()) {
        return ApiResponse.error(400, "question 不能为空");
    }

    String reply = aiService.recommend(question);
    return ApiResponse.success(reply);
}
}