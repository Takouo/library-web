package com.example.library.service;

import com.example.library.dao.BookDao;
import com.example.library.entity.Book;
import com.example.library.entity.LibraryStats;
import com.example.library.exception.BookAlreadyExistsException;
import com.example.library.exception.BookNotFoundException;
import com.example.library.exception.LibraryException;


import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BookService {
    private final BookDao bookDao;

    public BookService(BookDao bookDao) {
        this.bookDao = bookDao;
        System.out.println("BookService 被创建了，注入的 BookDao: " + bookDao);
    }
    public void addBook(String isbn, String title, String author) {
        if (bookDao.findByIsbn(isbn) != null) {
            throw new BookAlreadyExistsException(isbn);
        }
        bookDao.insert(new Book(isbn, title, author));
    }

    public void deleteBook(String isbn) {
        getBookOrThrow(isbn);
        bookDao.deleteByIsbn(isbn);
    }

    public void updateBook(String isbn, String title, String author) {
        Book book = getBookOrThrow(isbn);

        if (title != null && !title.isBlank()) {
            book.setTitle(title);
        }
        if (author != null && !author.isBlank()) {
            book.setAuthor(author);
        }

        // 目前 DAO 只提供了改 borrowed 的方法，改标题/作者要加一个方法
        bookDao.updateBookInfo(book);
    }

    public List<Book> findAll() {
        return bookDao.findAll();
    }

    public Book findByIsbn(String isbn) {
        return getBookOrThrow(isbn);
    }

    public void borrowBook(String isbn) {
        Book book = getBookOrThrow(isbn);

        if (book.isBorrowed()) {
            throw new LibraryException("图书已被借出: " + isbn);
        }

        bookDao.updateBorrowed(isbn, true);
    }

    public void returnBook(String isbn) {
        Book book = getBookOrThrow(isbn);

        if (!book.isBorrowed()) {
            throw new LibraryException("图书未被借出，无需归还: " + isbn);
        }

        bookDao.updateBorrowed(isbn, false);
    }

    public List<Book> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new LibraryException("搜索关键词不能为空");
        }
        return bookDao.searchByKeyword(keyword);
    }

    public List<Book> findByAuthor(String author) {
        if (author == null || author.isBlank()) {
            throw new LibraryException("作者名不能为空");
        }
        return bookDao.findByAuthor(author);
    }

    public LibraryStats getStats() {
        return bookDao.getStats();
    }

    private Book getBookOrThrow(String isbn) {
        Book book = bookDao.findByIsbn(isbn);
        if (book == null) {
            throw new BookNotFoundException(isbn);
        }
        return book;
    }


}