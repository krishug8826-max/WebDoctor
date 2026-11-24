package com.webdoctor.backend.scanner.dto;

import java.util.List;

public record SecurityAnalysisResponse(
        int securityScore,
        String grade,
        List<SecurityIssue> issues,
        String message
) {}