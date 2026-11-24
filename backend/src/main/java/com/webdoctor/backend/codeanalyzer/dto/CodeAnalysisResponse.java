package com.webdoctor.backend.codeanalyzer.dto;

import java.util.List;

public record CodeAnalysisResponse(
        String language,
        int score,
        int totalIssues,
        int errors,
        int warnings,
        int info,
        List<CodeFinding> findings
) {}