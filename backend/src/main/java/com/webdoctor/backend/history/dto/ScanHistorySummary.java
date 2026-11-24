package com.webdoctor.backend.history.dto;

import java.time.LocalDateTime;

public record ScanHistorySummary(

        Long id,

        String url,

        boolean reachable,

        int statusCode,

        long responseTimeMs,

        LocalDateTime scannedAt,

        Integer healthScore

) {
}