package com.webdoctor.backend.scanner.dto;

public record PerformanceFinding(
        String name,
        String status,
        String description,
        String recommendation
) {}