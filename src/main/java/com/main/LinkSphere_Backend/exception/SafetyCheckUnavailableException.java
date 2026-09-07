package com.main.LinkSphere_Backend.exception;

public class SafetyCheckUnavailableException extends RuntimeException {
    public SafetyCheckUnavailableException(String message) {
        super(message);
    }
}