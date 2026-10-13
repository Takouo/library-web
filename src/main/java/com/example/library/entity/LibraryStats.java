package com.example.library.entity;

public class LibraryStats {
    private final int total;
    private final int borrowed;
    private final int available;

    public LibraryStats(int total, int borrowed, int available) {
        this.total = total;
        this.borrowed = borrowed;
        this.available = available;
    }

    public int getTotal() {
        return total;
    }

    public int getBorrowed() {
        return borrowed;
    }

    public int getAvailable() {
        return available;
    }

    @Override
    public String toString() {
        return String.format(
                "总藏书: %d | 已借出: %d | 可借: %d",
                total, borrowed, available
        );
    }
}