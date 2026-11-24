package com.webdoctor.backend.scanner.dto;

public record SslAnalysisResponse(

        boolean secure,

        String domain,

        String issuer,

        String validFrom,

        String validUntil,

        long daysUntilExpiry,

        String message

) {}