package com.example.library.exception;

public class BookAlreadyExistsException extends LibraryException {
    public BookAlreadyExistsException(String isbn) {
        super("图书已存在: " + isbn);
    }
}