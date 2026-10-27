package com.example.library.service;

import com.example.library.dao.BookDao;
import com.example.library.entity.Book;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final BookDao bookDao;

    public AiService(BookDao bookDao) {
        this.bookDao = bookDao;
    }

    // 基础聊天（之前写的，保留）
    public String chat(String userMessage) {
        return callModel(userMessage);
    }

    // 用大模型提取关键词
    private String extractKeyword(String question) {
        String prompt = "请从下面这句话中，提取出最核心的1-2个用于搜索图书的实体名词（例如：Java、并发、算法、余华）。"
                + "只返回词语本身，多个词语用空格隔开，不要返回任何解释和标点符号。\n"
                + "用户的话：" + question;

        String result = callModel(prompt);
        return result == null ? "" : result.trim();
    }
    // 新增：基于图书库的智能推荐（RAG 核心）
    public String recommend(String question) {
        // 1. 先让大模型提取关键词（这一步代替了原本的整句搜索）
        String keyword = extractKeyword(question);
        System.out.println("大模型提取的关键词: " + keyword); // 打印出来看看

        // 2. 去数据库里检索相关图书（用提取出的关键词）
        List<Book> books = bookDao.searchByKeyword(keyword);

        // 3. 拼装参考资料
        StringBuilder context = new StringBuilder();
        if (books.isEmpty()) {
            context.append("（馆藏中暂未找到直接相关的图书）");
        } else {
            for (Book book : books) {
                context.append(String.format("- 《%s》，作者：%s\n",
                        book.getTitle(), book.getAuthor()));
            }
        }

        // 4. 构造更加严格的系统提示词
        String systemPrompt = "你是一个专业的图书管理员助手。请严格根据提供的参考资料回答用户问题。\n"
                + "如果资料中能找到答案，请推荐资料里的书；如果找不到，请只回答“抱歉，馆藏中暂时没有相关书籍”，绝对不要编造任何参考资料以外的书！\n\n"
                + "【参考资料】\n"
                + context.toString();

        // 5. 调用大模型生成回答
        return callModelWithSystem(systemPrompt, question);
    }

    // 底层调用：单轮对话
    private String callModel(String userMessage) {
        return callModelWithSystem(null, userMessage);
    }

    // 底层调用：带 System Prompt
    private String callModelWithSystem(String systemPrompt, String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "glm-4-flash");

        // 构造 messages
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        if (systemPrompt != null) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        body.put("messages", messages);
        body.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        Map response = restTemplate.postForObject(apiUrl, request, Map.class);

        if (response == null) return "模型无响应";
        List choices = (List) response.get("choices");
        if (choices == null || choices.isEmpty()) return "模型无返回内容";
        Map firstChoice = (Map) choices.get(0);
        Map message = (Map) firstChoice.get("message");
        return (String) message.get("content");
    }


}