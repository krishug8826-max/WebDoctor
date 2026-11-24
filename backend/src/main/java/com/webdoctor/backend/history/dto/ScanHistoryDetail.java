package com.webdoctor.backend.history.dto;

import com.webdoctor.backend.scanner.dto.ScanResponse;

import java.time.LocalDateTime;

public record ScanHistoryDetail(
        Long id,
        LocalDateTime scannedAt,
        ScanResponse scanResponse
) {
}