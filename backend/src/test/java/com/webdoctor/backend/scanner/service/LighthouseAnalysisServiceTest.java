package com.webdoctor.backend.scanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webdoctor.backend.scanner.dto.LighthouseAnalysisResponse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class LighthouseAnalysisServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private LighthouseAnalysisService createService(
            LighthouseAnalysisService.PageSpeedGateway gateway
    ) {

        return new LighthouseAnalysisService(mapper, gateway);
    }

    private JsonNode createResponse() throws Exception {

        String json = """
                {
                  "lighthouseResult": {
                    "finalUrl": "https://example.com/",
                    "fetchTime": "2026-11-25T00:00:00.000Z",

                    "categories": {
                      "performance": {
                        "score": 0.95,
                        "auditRefs": [
                          {
                            "id": "unused-javascript",
                            "group": "load-opportunities"
                          },
                          {
                            "id": "diagnostic-test",
                            "group": "diagnostics"
                          }
                        ]
                      },
                      "accessibility": {
                        "score": 0.98,
                        "auditRefs": []
                      },
                      "best-practices": {
                        "score": 0.96,
                        "auditRefs": []
                      },
                      "seo": {
                        "score": 0.99,
                        "auditRefs": []
                      }
                    },

                    "audits": {
                      "first-contentful-paint": {
                        "numericValue": 1200,
                        "displayValue": "1.2 s",
                        "score": 0.9
                      },
                      "largest-contentful-paint": {
                        "numericValue": 2200,
                        "displayValue": "2.2 s",
                        "score": 0.8
                      },
                      "total-blocking-time": {
                        "numericValue": 100,
                        "displayValue": "100 ms",
                        "score": 0.9
                      },
                      "cumulative-layout-shift": {
                        "numericValue": 0.01,
                        "displayValue": "0.01",
                        "score": 1
                      },
                      "speed-index": {
                        "numericValue": 1500,
                        "displayValue": "1.5 s",
                        "score": 0.9
                      },
                      "unused-javascript": {
                        "title": "Reduce unused JavaScript",
                        "description": "Reduce unused JavaScript.",
                        "displayValue": "Potential savings of 300 ms",
                        "score": 0.5,
                        "scoreDisplayMode": "numeric",
                        "numericValue": 300,
                        "numericUnit": "millisecond",
                        "details": {
                          "overallSavingsMs": 300,
                          "overallSavingsBytes": 12000
                        }
                      },
                      "diagnostic-test": {
                        "title": "Test diagnostic",
                        "description": "Diagnostic information.",
                        "displayValue": "Diagnostic details",
                        "score": 1,
                        "scoreDisplayMode": "informative",
                        "numericValue": 50,
                        "numericUnit": "millisecond",
                        "details": {}
                      }
                    }
                  }
                }
                """;

        return mapper.readTree(json);
    }

    @Test
    void shouldCompleteMobileAndDesktopAudits() throws Exception {

        List<String> strategies = new ArrayList<>();

        LighthouseAnalysisService service =
                createService((url, strategy) -> {

                    assertEquals("https://example.com", url);
                    strategies.add(strategy);

                    return createResponse();
                });

        LighthouseAnalysisResponse result =
                service.analyze("https://example.com");

        assertEquals("COMPLETED", result.status());

        assertEquals("COMPLETED", result.mobile().status());
        assertEquals("COMPLETED", result.desktop().status());

        assertEquals(95, result.mobile().performanceScore());
        assertEquals(95, result.desktop().performanceScore());

        assertEquals(98, result.mobile().accessibilityScore());
        assertEquals(96, result.mobile().bestPracticesScore());
        assertEquals(99, result.mobile().seoScore());

        assertEquals(2, strategies.size());
        assertEquals(List.of("mobile", "desktop"), strategies);

        assertNotNull(result.mobile().fcp());
        assertEquals(1200.0, result.mobile().fcp().value());
        assertEquals("1.2 s", result.mobile().fcp().displayValue());

        assertEquals(300.0,
                result.mobile().opportunities().get(0).estimatedSavingsMs());

        assertEquals(1, result.mobile().diagnostics().size());
        assertEquals(1, result.mobile().failedAudits().size());
    }

    @Test
    void shouldReturnPartialWhenDesktopAuditFails() throws Exception {

        LighthouseAnalysisService service =
                createService((url, strategy) -> {

                    if ("desktop".equals(strategy)) {
                        throw new IOException("Desktop audit failed");
                    }

                    return createResponse();
                });

        LighthouseAnalysisResponse result =
                service.analyze("https://example.com");

        assertEquals("PARTIAL", result.status());

        assertEquals("COMPLETED", result.mobile().status());
        assertEquals("FAILED", result.desktop().status());
    }

    @Test
    void shouldReturnFailedWhenBothAuditsFail() {

        LighthouseAnalysisService service =
                createService((url, strategy) -> {
                    throw new IOException("Simulated PageSpeed API failure");
                });

        LighthouseAnalysisResponse result =
                service.analyze("https://example.com");

        assertEquals("FAILED", result.status());

        assertEquals("FAILED", result.mobile().status());
        assertEquals("FAILED", result.desktop().status());
    }

    @Test
    void shouldHandleInvalidApiResponse() throws Exception {

        LighthouseAnalysisService service =
                createService((url, strategy) ->
                        mapper.readTree("{\"error\":{\"message\":\"Invalid API response\"}}")
                );

        LighthouseAnalysisResponse result =
                service.analyze("https://example.com");

        assertEquals("FAILED", result.status());

        assertEquals("FAILED", result.mobile().status());
        assertEquals("FAILED", result.desktop().status());
    }

    @Test
    void shouldHandleApiTimeout() {

        LighthouseAnalysisService service =
                createService((url, strategy) -> {
                    throw new TimeoutException("PageSpeed API timed out");
                });

        LighthouseAnalysisResponse result =
                service.analyze("https://example.com");

        assertEquals("FAILED", result.status());

        assertEquals("FAILED", result.mobile().status());
        assertEquals("FAILED", result.desktop().status());
    }
}