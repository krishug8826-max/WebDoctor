package com.webdoctor.backend.scanner.dto;

import java.util.List;

public record PerformanceAnalysisResponse(
        int performanceScore,
        String grade,
        long responseTimeMs,
        long contentLengthBytes,
        String contentType,
        String compression,
        List<PerformanceFinding> findings,
        String message
) {}