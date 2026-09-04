package com.main.LinkSphere_Backend.exception;

public class UnsafeUrlException extends RuntimeException {
    public UnsafeUrlException(String message) {
        super(message);
    }
}