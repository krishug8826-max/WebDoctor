package com.webdoctor.backend.scanner.dto;

public record ApiFinding(

        String endpoint,
        String method,

        int statusCode,
        long responseTimeMs,

        String status,
        String responseCategory,

        String contentType,

        boolean jsonResponse,
        boolean validJson,

        String message,
        String recommendation,

        String detectionSource,
        String confidence

) {}