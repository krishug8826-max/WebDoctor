package com.webdoctor.backend.scanner.dto;

import java.util.List;

public record BrokenLinkAnalysisResponse(

        int totalLinks,

        int workingLinks,

        int brokenLinks,

        int redirectedLinks,

        int skippedLinks,

        List<BrokenLinkFinding> findings,

        String message

) {}