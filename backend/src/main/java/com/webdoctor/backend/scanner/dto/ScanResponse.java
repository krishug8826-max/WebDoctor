package com.webdoctor.backend.scanner.dto;

public record ScanResponse(

        String url,

        boolean reachable,

        int statusCode,

        long responseTimeMs,

        String message,

        SslAnalysisResponse sslAnalysis,

        SecurityAnalysisResponse securityAnalysis,

        PerformanceAnalysisResponse performanceAnalysis,

        SeoAnalysisResponse seoAnalysis,

        BrokenLinkAnalysisResponse brokenLinkAnalysis,

        JavaScriptAnalysisResponse javascriptAnalysis,

        MobileAnalysisResponse mobileAnalysis,

        ApiAnalysisResponse apiAnalysis,

        LighthouseAnalysisResponse lighthouseAnalysis

) {}