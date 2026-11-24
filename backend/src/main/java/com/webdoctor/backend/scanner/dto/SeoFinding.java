package com.webdoctor.backend.scanner.dto;

public record SeoFinding(
        String name,
        String status,
        String description,
        String recommendation
) {}