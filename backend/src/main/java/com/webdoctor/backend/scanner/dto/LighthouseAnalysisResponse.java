package com.webdoctor.backend.scanner.dto;

import java.util.List;

public record LighthouseAnalysisResponse(
        String status,
        LighthouseResult mobile,
        LighthouseResult desktop,
        String message
) {

    public record LighthouseResult(
            String status,
            String strategy,
            Integer performanceScore,
            LighthouseMetric fcp,
            LighthouseMetric lcp,
            LighthouseMetric tbt,
            LighthouseMetric cls,
            LighthouseMetric speedIndex,
            String finalUrl,
            String fetchTime,

            Integer accessibilityScore,
            Integer bestPracticesScore,
            Integer seoScore,

            List<LighthouseAudit> opportunities,
            List<LighthouseAudit> diagnostics,
            List<LighthouseAudit> failedAudits
    ) {

        // Backward compatibility with existing constructor
        public LighthouseResult(
                String status,
                String strategy,
                Integer performanceScore,
                LighthouseMetric fcp,
                LighthouseMetric lcp,
                LighthouseMetric tbt,
                LighthouseMetric cls,
                LighthouseMetric speedIndex,
                String finalUrl,
                String fetchTime
        ) {
            this(
                    status,
                    strategy,
                    performanceScore,
                    fcp,
                    lcp,
                    tbt,
                    cls,
                    speedIndex,
                    finalUrl,
                    fetchTime,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }

    public record LighthouseMetric(
            Double value,
            String displayValue,
            Double score
    ) {
    }

    public record LighthouseAudit(
            String id,
            String title,
            String description,
            String displayValue,
            Double score,
            String scoreDisplayMode,
            Double numericValue,
            String numericUnit,
            Double estimatedSavingsMs,
            Double estimatedSavingsBytes
    ) {
    }
}