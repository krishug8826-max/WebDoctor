package com.webdoctor.backend.codeanalyzer.dto;

public record CodeFinding(
        String ruleId,
        String severity,
        String title,
        String message,
        int line,
        String suggestion
) {}