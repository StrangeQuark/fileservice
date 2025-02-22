package com.strangequark.fileservice.error;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class ErrorResponse {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy hh:mm:ss")
    private final LocalDateTime timestamp;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private String errorMessage;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(String errorMessage) {
        this();
        this.errorMessage = errorMessage;
    }
}
