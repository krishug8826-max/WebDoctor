package com.webdoctor.backend.scanner.dto;

import java.util.List;

public record SeoAnalysisResponse(
        int seoScore,
        String grade,
        String pageTitle,
        String metaDescription,
        String h1Heading,
        int imageCount,
        int imagesWithoutAlt,
        List<SeoFinding> findings,
        String message
) {}