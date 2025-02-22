package com.strangequark.fileservice.upload;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class UploadResponse {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy hh:mm:ss")
    private final LocalDateTime timestamp;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private String message;

    public UploadResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public UploadResponse(String message) {
        this();
        this.message = message;
    }
}
