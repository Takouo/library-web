package com.example.library.dao;

import com.example.library.entity.Book;
import com.example.library.entity.LibraryStats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BookDao {

    private final JdbcTemplate jdbcTemplate;

    public BookDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Book> rowMapper = (rs, rowNum) -> {
        Book book = new Book(
                rs.getString("isbn"),
                rs.getString("title"),
                rs.getString("author")
        );
        book.setBorrowed(rs.getInt("borrowed") == 1);
        return book;
    };

    public void insert(Book book) {
        String sql = "INSERT INTO books (isbn, title, author, borrowed) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                book.getIsbn(),
                book.getTitle(),
                book.getAuthor(),
                book.isBorrowed() ? 1 : 0);
    }

    public Book findByIsbn(String isbn) {
        String sql = "SELECT * FROM books WHERE isbn = ?";
        List<Book> list = jdbcTemplate.query(sql, rowMapper, isbn);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Book> findAll() {
        String sql = "SELECT * FROM books";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public void updateBorrowed(String isbn, boolean borrowed) {
        String sql = "UPDATE books SET borrowed = ? WHERE isbn = ?";
        jdbcTemplate.update(sql, borrowed ? 1 : 0, isbn);
    }

    public void updateBookInfo(Book book) {
        String sql = "UPDATE books SET title = ?, author = ? WHERE isbn = ?";
        jdbcTemplate.update(sql, book.getTitle(), book.getAuthor(), book.getIsbn());
    }

    public void deleteByIsbn(String isbn) {
        String sql = "DELETE FROM books WHERE isbn = ?";
        jdbcTemplate.update(sql, isbn);
    }

    public List<Book> searchByKeyword(String keyword) {
        String sql = "SELECT * FROM books WHERE title LIKE ? OR author LIKE ?";
        String pattern = "%" + keyword + "%";
        return jdbcTemplate.query(sql, rowMapper, pattern, pattern);
    }

    public List<Book> findByAuthor(String author) {
        String sql = "SELECT * FROM books WHERE author LIKE ?";
        String pattern = "%" + author + "%";
        return jdbcTemplate.query(sql, rowMapper, pattern);
    }

    public LibraryStats getStats() {
        String sql = "SELECT COUNT(*) AS total, COALESCE(SUM(borrowed), 0) AS borrowed FROM books";
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            int total = rs.getInt("total");
            int borrowed = rs.getInt("borrowed");
            return new LibraryStats(total, borrowed, total - borrowed);
        });
    }
}