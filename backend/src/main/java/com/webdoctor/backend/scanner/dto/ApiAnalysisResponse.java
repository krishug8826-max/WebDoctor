package com.webdoctor.backend.scanner.dto;

import java.util.List;

public record ApiAnalysisResponse(

        int apiHealthScore,
        String grade,
        String healthStatus,

        int totalEndpoints,
        int checkedEndpoints,

        int workingEndpoints,
        int brokenEndpoints,
        int redirectedEndpoints,
        int authenticationRequiredEndpoints,
        int errorEndpoints,
        int skippedEndpoints,

        int jsonEndpoints,
        int invalidJsonEndpoints,

        long averageResponseTimeMs,
        long fastestResponseTimeMs,
        long slowestResponseTimeMs,

        int totalScriptsFound,
        int inlineScriptsScanned,
        int externalScriptsFound,
        int externalScriptsDownloaded,

        int apiPatternsDetected,

        List<ApiFinding> findings,
        List<String> recommendations,

        String message

) {}