package com.example.library.service;

import com.example.library.dao.BookDao;
import com.example.library.entity.Book;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpringAgentService {

    private final ChatClient chatClient;
    private final BookDao bookDao;

    public SpringAgentService(ChatClient.Builder builder, BookDao bookDao) {
        this.bookDao = bookDao;

        // ★ 核心：把当前类里的 @Tool 方法注册给大模型
        this.chatClient = builder
                .defaultTools(this)
                .build();
    }

    public String agent(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    // ================= 定义工具 =================

    @Tool(description = "根据关键词搜索馆藏图书，返回图书列表")
    public List<Book> searchBooks(String keyword) {
        System.out.println(">>> [Spring AI] 正在搜索图书: " + keyword);
        return bookDao.searchByKeyword(keyword);
    }

    @Tool(description = "根据图书的ISBN码借书")
    public String borrowBook(String isbn) {
        System.out.println(">>> [Spring AI] 正在借书，ISBN: " + isbn);
        // 为了不依赖 BookService，这里简单演示
        return "借书成功，请及时领取。";
    }
}