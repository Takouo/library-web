package com.example.library.service;

import com.example.library.dao.BookDao;
import com.example.library.entity.Book;
import com.example.library.entity.LibraryStats;
import com.example.library.exception.BookAlreadyExistsException;
import com.example.library.exception.BookNotFoundException;
import com.example.library.exception.LibraryException;

import java.sql.SQLException;
import java.util.List;

public class BookService {
    private final BookDao bookDao = new BookDao();

    public void addBook(String isbn, String title, String author) throws SQLException {
        if (bookDao.findByIsbn(isbn) != null) {
            throw new BookAlreadyExistsException(isbn);
        }
        bookDao.insert(new Book(isbn, title, author));
    }

    public void deleteBook(String isbn) throws SQLException {
        getBookOrThrow(isbn);
        bookDao.deleteByIsbn(isbn);
    }

    public void updateBook(String isbn, String title, String author) throws SQLException {
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

    public List<Book> findAll() throws SQLException {
        return bookDao.findAll();
    }

    public Book findByIsbn(String isbn) throws SQLException {
        return getBookOrThrow(isbn);
    }

    public void borrowBook(String isbn) throws SQLException {
        Book book = getBookOrThrow(isbn);

        if (book.isBorrowed()) {
            throw new LibraryException("图书已被借出: " + isbn);
        }

        bookDao.updateBorrowed(isbn, true);
    }

    public void returnBook(String isbn) throws SQLException {
        Book book = getBookOrThrow(isbn);

        if (!book.isBorrowed()) {
            throw new LibraryException("图书未被借出，无需归还: " + isbn);
        }

        bookDao.updateBorrowed(isbn, false);
    }

    public List<Book> searchByKeyword(String keyword) throws SQLException {
        if (keyword == null || keyword.isBlank()) {
            throw new LibraryException("搜索关键词不能为空");
        }
        return bookDao.searchByKeyword(keyword);
    }

    public List<Book> findByAuthor(String author) throws SQLException {
        if (author == null || author.isBlank()) {
            throw new LibraryException("作者名不能为空");
        }
        return bookDao.findByAuthor(author);
    }

    public LibraryStats getStats() throws SQLException {
        return bookDao.getStats();
    }

    private Book getBookOrThrow(String isbn) throws SQLException {
        Book book = bookDao.findByIsbn(isbn);
        if (book == null) {
            throw new BookNotFoundException(isbn);
        }
        return book;
    }
}