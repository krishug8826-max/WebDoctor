package com.webdoctor.backend.scanner.dto;

import java.util.List;

public record JavaScriptAnalysisResponse(

        int totalScripts,

        int checkedScripts,

        int workingScripts,

        int brokenScripts,

        int redirectedScripts,

        int errorScripts,

        int skippedScripts,

        List<JavaScriptFinding> findings,

        String message

) {}