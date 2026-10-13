package com.example.library.controller;

import com.example.library.entity.Book;
import com.example.library.entity.LibraryStats;
import com.example.library.service.BookService;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService bookService = new BookService();

    // 查询全部
    @GetMapping
    public List<Book> list() throws SQLException {
        return bookService.findAll();
    }

    // 按 ISBN 查询
    @GetMapping("/{isbn}")
    public Book getByIsbn(@PathVariable String isbn) throws SQLException {
        return bookService.findByIsbn(isbn);
    }

    // 添加图书
    @PostMapping
    public String add(@RequestBody Book book) throws SQLException {
        bookService.addBook(book.getIsbn(), book.getTitle(), book.getAuthor());
        return "添加成功";
    }

    // 删除图书
    @DeleteMapping("/{isbn}")
    public String delete(@PathVariable String isbn) throws SQLException {
        bookService.deleteBook(isbn);
        return "删除成功";
    }

    // 借书
    @PostMapping("/{isbn}/borrow")
    public String borrow(@PathVariable String isbn) throws SQLException {
        bookService.borrowBook(isbn);
        return "借书成功";
    }

    // 还书
    @PostMapping("/{isbn}/return")
    public String returnBook(@PathVariable String isbn) throws SQLException {
        bookService.returnBook(isbn);
        return "还书成功";
    }

    // 模糊搜索
    @GetMapping("/search")
    public List<Book> search(@RequestParam String keyword) throws SQLException {
        return bookService.searchByKeyword(keyword);
    }

    // 统计
    @GetMapping("/stats")
    public LibraryStats stats() throws SQLException {
        return bookService.getStats();
    }
}