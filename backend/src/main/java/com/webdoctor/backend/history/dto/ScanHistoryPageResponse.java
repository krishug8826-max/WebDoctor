package com.webdoctor.backend.history.dto;

import java.util.List;

public record ScanHistoryPageResponse(

        List<ScanHistorySummary> content,

        int page,

        int size,

        long totalElements,

        int totalPages,

        boolean hasNext,

        boolean hasPrevious

) {
}