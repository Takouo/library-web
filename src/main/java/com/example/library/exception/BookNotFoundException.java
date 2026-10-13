package com.example.library.exception;

public class BookNotFoundException extends LibraryException {
    public BookNotFoundException(String isbn) {
        super("图书不存在: " + isbn);
    }
}