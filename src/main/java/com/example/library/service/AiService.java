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
    private final BookService bookService; // 新增


    public AiService(BookDao bookDao, BookService bookService) { // 修改构造器
        this.bookDao = bookDao;
        this.bookService = bookService;
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

    public String reactAgent(String userMessage) {
        // 1. 准备对话历史（ReAct 的核心是维护上下文）
        List<Map<String, Object>> messages = new java.util.ArrayList<>();

        // ★ 新增：ReAct 专用的 System Prompt
        String systemPrompt = "你是一个强大的智能图书助手，拥有调用工具的能力。\n" +
                "遇到用户请求时，你必须遵循以下原则：\n" +
                "1. 如果用户没有提供具体的 ISBN，你必须先调用 searchBooks 工具查询图书的详细信息（包括 ISBN）。\n" +
                "2. 只有在获取到明确的 ISBN 后，才能调用 borrowBook 工具借书。\n" +
                "3. 尽量自己通过工具解决问题，不要直接把问题抛回给用户。";
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // 用户的实际请求
        messages.add(Map.of("role", "user", "content", userMessage));

        // 2. 定义我们提供的工具（查书 & 借书）
        Map<String, Object> searchTool = Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "searchBooks",
                        "description", "根据关键词搜索馆藏图书",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of("keyword", Map.of("type", "string")),
                                "required", List.of("keyword")
                        )
                )
        );

        Map<String, Object> borrowTool = Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "borrowBook",
                        "description", "根据图书的ISBN码借书",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of("isbn", Map.of("type", "string")),
                                "required", List.of("isbn")
                        )
                )
        );

        List<Map<String, Object>> tools = List.of(searchTool, borrowTool);

        // 3. 开启 ReAct 循环（设置最大循环次数，防止死循环）
        int maxIterations = 5;
        for (int i = 0; i < maxIterations; i++) {
            System.out.println("=== ReAct 第 " + (i + 1) + " 轮循环 ===");

            // 3.1 每次循环，都把当前所有的 messages 和 tools 发给大模型
            Map<String, Object> body = new HashMap<>();
            body.put("model", "glm-4-flash");
            body.put("messages", messages);
            body.put("tools", tools);
            body.put("tool_choice", "auto");

            Map response = postToLlm(body);
            Map firstChoice = (Map) ((List) response.get("choices")).get(0);
            Map message = (Map) firstChoice.get("message");

            // 3.2 把大模型这次的回复加入历史（非常重要！）
            messages.add(message);

            List toolCalls = (List) message.get("tool_calls");

            // 3.3 如果没有工具调用，说明它想好了，直接返回最终回答
            if (toolCalls == null || toolCalls.isEmpty()) {
                System.out.println(">>> Agent 结束思考，给出最终回答。");
                return (String) message.get("content");
            }

            // 3.4 如果要调工具，遍历所有请求（有时模型一口气要求调多个）
            for (Object tcObj : toolCalls) {
                Map toolCall = (Map) tcObj;
                String callId = (String) toolCall.get("id");
                Map function = (Map) toolCall.get("function");
                String funcName = (String) function.get("name");
                String argsStr = (String) function.get("arguments");

                System.out.println(">>> 大模型决定调用工具: " + funcName + "，参数: " + argsStr);

                // 3.5 在 Java 这边执行真正的代码
                String toolResult = executeTool(funcName, argsStr);
                System.out.println(">>> 工具执行结果: " + toolResult);

                // 3.6 把工具执行的结果，封装成 "tool" 角色，塞回对话历史
                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", callId,
                        "content", toolResult
                ));
            }
            // 循环继续，带着新的观察结果去问大模型
        }

        return "抱歉，任务太复杂，超出了我的处理极限。";
    }

    // 执行工具的统一入口
    private String executeTool(String funcName, String argsStr) {
        try {
            if ("searchBooks".equals(funcName)) {
                String keyword = argsStr.split("\"keyword\"\\s*:\\s*\"")[1].split("\"")[0];
                List<Book> books = bookDao.searchByKeyword(keyword);
                if (books.isEmpty()) return "未找到相关图书。";
                StringBuilder sb = new StringBuilder();
                for (Book b : books) {
                    sb.append(String.format("书名:《%s》, ISBN:%s, 状态:%s\n",
                            b.getTitle(), b.getIsbn(), b.isBorrowed() ? "已借出" : "可借"));
                }
                return sb.toString();

            } else if ("borrowBook".equals(funcName)) {
                String isbn = argsStr.split("\"isbn\"\\s*:\\s*\"")[1].split("\"")[0];
                bookService.borrowBook(isbn); // 调用你写好的业务逻辑
                return "借书成功。";
            }
        } catch (Exception e) {
            return "工具执行失败: " + e.getMessage();
        }
        return "未知工具";
    }

    // 抽取一个发请求的方法，免得重复写
    private Map postToLlm(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        return restTemplate.postForObject(apiUrl, request, Map.class);
    }
}