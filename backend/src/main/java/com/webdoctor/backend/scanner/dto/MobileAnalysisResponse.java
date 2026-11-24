package com.webdoctor.backend.scanner.dto;

import java.util.List;

public record MobileAnalysisResponse(

        int mobileScore,

        String grade,

        boolean viewportConfigured,

        boolean responsiveViewport,

        int imageCount,

        int imagesWithFixedWidth,

        int fixedWidthElements,

        int tableCount,

        List<MobileFinding> findings,

        String message

) {}