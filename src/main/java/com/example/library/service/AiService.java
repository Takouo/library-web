package com.example.library.service;

import com.example.library.dao.BookDao;
import com.example.library.entity.Book;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
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

    public String chatWithTools(String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "glm-4-flash");
        body.put("messages", List.of(Map.of("role", "user", "content", userMessage)));

        // ★ 1. 定义工具描述（告诉大模型你有什么工具）
        Map<String, Object> searchTool = new HashMap<>();
        searchTool.put("type", "function");
        searchTool.put("function", Map.of(
                "name", "searchBooks",
                "description", "根据关键词搜索馆藏图书，返回图书列表",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "keyword", Map.of(
                                        "type", "string",
                                        "description", "要搜索的图书关键词，例如：Java、并发、余华"
                                )
                        ),
                        "required", List.of("keyword")
                )
        ));

        body.put("tools", List.of(searchTool));
        body.put("tool_choice", "auto"); // 让模型自己决定要不要调用

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map response = restTemplate.postForObject(apiUrl, request, Map.class);

        // ★ 2. 解析大模型的返回
        if (response == null) return "模型无响应";
        List choices = (List) response.get("choices");
        if (choices == null || choices.isEmpty()) return "模型无返回内容";
        Map firstChoice = (Map) choices.get(0);
        Map message = (Map) firstChoice.get("message");

        // ★ 3. 检查模型是不是要调用工具
        List toolCalls = (List) message.get("tool_calls");
        if (toolCalls == null || toolCalls.isEmpty()) {
            // 如果没调工具，说明它自己直接回答了（比如"今天天气不错"）
            return (String) message.get("content");
        }

        // ★ 4. 模型要调工具，我们执行对应的 Java 代码
        Map firstCall = (Map) toolCalls.get(0);
        Map function = (Map) firstCall.get("function");
        String functionName = (String) function.get("name");
        String argumentsStr = (String) function.get("arguments"); // 这是大模型传过来的 JSON 字符串

        System.out.println(">>> 大模型要调用的工具: " + functionName);
        System.out.println(">>> 大模型传来的参数: " + argumentsStr);

        // 这里为了简单，我们直接用字符串切割提取参数（生产环境应该用 Jackson/Gson 解析 JSON）
        String keyword = "";
        if (argumentsStr.contains("\"keyword\"")) {
            keyword = argumentsStr.split("\"keyword\"\\s*:\\s*\"")[1].split("\"")[0];
        }

        // 执行真正的 Java 方法
        List<Book> books = bookDao.searchByKeyword(keyword);

        // 把查询结果转成字符串
        StringBuilder context = new StringBuilder();
        for (Book book : books) {
            context.append(String.format("- 《%s》，作者：%s\n", book.getTitle(), book.getAuthor()));
        }
        if (context.length() == 0) context.append("无相关图书");

        System.out.println(">>> 数据库查询结果: \n" + context);

        // ★ 5. 把查询结果再次发给大模型，让它总结
        return secondCallWithToolResult(userMessage, argumentsStr, keyword, context.toString());
    }

    // 带着工具执行结果，第二次请求大模型生成最终回复
    private String secondCallWithToolResult(String userMessage, String toolArgs, String keyword, String toolResult) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "glm-4-flash");

        // 构造包含完整对话历史的 messages
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userMessage));

        // 伪造一个大模型请求工具的 assistant 消息
        messages.add(Map.of(
                "role", "assistant",
                "content", "",
                "tool_calls", List.of(Map.of(
                        "id", "call_123",
                        "type", "function",
                        "function", Map.of("name", "searchBooks", "arguments", toolArgs)
                ))
        ));

        // 提供一个 tool 角色的消息，告诉大模型工具执行结果
        messages.add(Map.of(
                "role", "tool",
                "tool_call_id", "call_123",
                "content", toolResult
        ));

        body.put("messages", messages);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map response = restTemplate.postForObject(apiUrl, request, Map.class);

        // 解析最终回复
        List choices = (List) response.get("choices");
        Map firstChoice = (Map) choices.get(0);
        Map message = (Map) firstChoice.get("message");
        return (String) message.get("content");
    }

}