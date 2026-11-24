package com.webdoctor.backend.scanner.dto;

public record SecurityIssue(
        String name,
        String status,
        String riskLevel,
        String description,
        String recommendation
) {}