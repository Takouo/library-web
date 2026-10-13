package com.example.library.entity;

public class Book {
    private String isbn;
    private String title;
    private String author;
    private boolean borrowed;

    public Book(String isbn, String title, String author) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.borrowed = false;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public boolean isBorrowed() {
        return borrowed;
    }

    public void setBorrowed(boolean borrowed) {
        this.borrowed = borrowed;
    }

    @Override
    public String toString() {
        return String.format(
                "ISBN: %s | 书名: %s | 作者: %s | 状态: %s",
                isbn, title, author, borrowed ? "已借出" : "可借"
        );
    }
}