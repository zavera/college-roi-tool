package com.example.collegeroitool.service;

/** Thrown when a user has hit their monthly Claude spend cap (see TokenUsageService). */
public class MonthlyCostCapExceededException extends RuntimeException {
    public MonthlyCostCapExceededException(String message) {
        super(message);
    }
}
