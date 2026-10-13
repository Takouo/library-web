package com.example.library.dao;

import com.example.library.entity.Book;
import com.example.library.entity.LibraryStats;

import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class BookDao {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = BookDao.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            PROPS.load(in);
        } catch (Exception e) {
            throw new RuntimeException("加载数据库配置失败", e);
        }
    }

    private Connection getConnection() throws SQLException {
        String url = PROPS.getProperty("spring.datasource.url");
        String user = PROPS.getProperty("spring.datasource.username");
        String password = PROPS.getProperty("spring.datasource.password");
        return DriverManager.getConnection(url, user, password);
    }

    public void insert(Book book) throws SQLException {
        String sql = "INSERT INTO books (isbn, title, author, borrowed) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, book.getIsbn());
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getAuthor());
            ps.setInt(4, book.isBorrowed() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public Book findByIsbn(String isbn) throws SQLException {
        String sql = "SELECT * FROM books WHERE isbn = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Book> findAll() throws SQLException {
        String sql = "SELECT * FROM books";
        List<Book> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    public void updateBorrowed(String isbn, boolean borrowed) throws SQLException {
        String sql = "UPDATE books SET borrowed = ? WHERE isbn = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, borrowed ? 1 : 0);
            ps.setString(2, isbn);
            ps.executeUpdate();
        }
    }

    public void deleteByIsbn(String isbn) throws SQLException {
        String sql = "DELETE FROM books WHERE isbn = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            ps.executeUpdate();
        }
    }

    private Book mapRow(ResultSet rs) throws SQLException {
        Book book = new Book(
                rs.getString("isbn"),
                rs.getString("title"),
                rs.getString("author")
        );
        book.setBorrowed(rs.getInt("borrowed") == 1);
        return book;
    }

    public void updateBookInfo(Book book) throws SQLException {
        String sql = "UPDATE books SET title = ?, author = ? WHERE isbn = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, book.getTitle());
            ps.setString(2, book.getAuthor());
            ps.setString(3, book.getIsbn());
            ps.executeUpdate();
        }
    }

    public List<Book> searchByKeyword(String keyword) throws SQLException {
        String sql = "SELECT * FROM books WHERE title LIKE ? OR author LIKE ?";
        List<Book> result = new ArrayList<>();
        String pattern = "%" + keyword + "%";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public List<Book> findByAuthor(String author) throws SQLException {
        String sql = "SELECT * FROM books WHERE author LIKE ?";
        List<Book> result = new ArrayList<>();
        String pattern = "%" + author + "%";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public LibraryStats getStats() throws SQLException {
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(borrowed), 0) AS borrowed FROM books";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int total = rs.getInt("total");
                int borrowed = rs.getInt("borrowed");
                return new LibraryStats(total, borrowed, total - borrowed);
            }
        }
        return new LibraryStats(0, 0, 0);
    }
}