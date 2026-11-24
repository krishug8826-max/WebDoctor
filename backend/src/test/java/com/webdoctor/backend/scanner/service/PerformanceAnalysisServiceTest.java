package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.PerformanceAnalysisResponse;
import com.webdoctor.backend.scanner.dto.PerformanceFinding;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceAnalysisServiceTest {

    private final PerformanceAnalysisService service =
            new PerformanceAnalysisService();

    @Test
    void shouldGiveFullScoreForFastWebsite() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("100000"),
                "Content-Type", List.of("text/html"),
                "Content-Encoding", List.of("gzip"),
                "Cache-Control", List.of("max-age=3600")
        );

        PerformanceAnalysisResponse result =
                service.analyze(200, headers);

        assertEquals(100, result.performanceScore());
        assertEquals("A", result.grade());
        assertEquals(200, result.responseTimeMs());
        assertEquals(100000, result.contentLengthBytes());
        assertEquals("text/html", result.contentType());
        assertEquals("gzip", result.compression());
        assertEquals("Performance analysis completed.", result.message());
    }

    @Test
    void shouldDeduct10PointsForAverageResponseTime() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("100000")
        );

        PerformanceAnalysisResponse result =
                service.analyze(500, headers);

        assertEquals(90, result.performanceScore());
        assertEquals("A", result.grade());
    }

    @Test
    void shouldDeduct25PointsForSlowResponseTime() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("100000")
        );

        PerformanceAnalysisResponse result =
                service.analyze(1000, headers);

        assertEquals(75, result.performanceScore());
        assertEquals("B", result.grade());
    }

    @Test
    void shouldDeduct40PointsForVerySlowResponseTime() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("100000")
        );

        PerformanceAnalysisResponse result =
                service.analyze(1500, headers);

        assertEquals(60, result.performanceScore());
        assertEquals("C", result.grade());
    }

    @Test
    void shouldDeduct10PointsForMediumResponseSize() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("1000000")
        );

        PerformanceAnalysisResponse result =
                service.analyze(100, headers);

        assertEquals(90, result.performanceScore());
        assertEquals("A", result.grade());
    }

    @Test
    void shouldDeduct20PointsForLargeResponseSize() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("3000000")
        );

        PerformanceAnalysisResponse result =
                service.analyze(100, headers);

        assertEquals(80, result.performanceScore());
        assertEquals("B", result.grade());
    }

    @Test
    void shouldNotDeductPointsWhenContentLengthIsMissing() {

        Map<String, List<String>> headers = Map.of();

        PerformanceAnalysisResponse result =
                service.analyze(100, headers);

        assertEquals(100, result.performanceScore());
        assertEquals(0, result.contentLengthBytes());

        assertTrue(result.findings().stream().anyMatch(
                finding -> finding.name().equals("Response Size")
                        && finding.status().equals("UNKNOWN")
        ));
    }

    @Test
    void shouldDetectCompressionAndCacheControl() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("100000"),
                "Content-Encoding", List.of("br"),
                "Cache-Control", List.of("public, max-age=3600")
        );

        PerformanceAnalysisResponse result =
                service.analyze(100, headers);

        assertEquals("br", result.compression());

        assertTrue(result.findings().stream().anyMatch(
                finding -> finding.name().equals("Response Compression")
                        && finding.status().equals("DETECTED")
        ));

        assertTrue(result.findings().stream().anyMatch(
                finding -> finding.name().equals("Browser Caching")
                        && finding.status().equals("DETECTED")
        ));
    }

    @Test
    void shouldDetectMissingCompressionAndCacheControl() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("100000")
        );

        PerformanceAnalysisResponse result =
                service.analyze(100, headers);

        assertEquals("Not Detected", result.compression());

        assertTrue(result.findings().stream().anyMatch(
                finding -> finding.name().equals("Response Compression")
                        && finding.status().equals("NOT_DETECTED")
        ));

        assertTrue(result.findings().stream().anyMatch(
                finding -> finding.name().equals("Browser Caching")
                        && finding.status().equals("NOT_DETECTED")
        ));
    }

    @Test
    void shouldHandleMissingContentType() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("100000")
        );

        PerformanceAnalysisResponse result =
                service.analyze(100, headers);

        assertEquals("Not Available", result.contentType());

        assertTrue(result.findings().stream().anyMatch(
                finding -> finding.name().equals("Content Type")
                        && finding.status().equals("UNKNOWN")
        ));
    }

    @Test
    void shouldHandleInvalidContentLength() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("invalid")
        );

        PerformanceAnalysisResponse result =
                service.analyze(100, headers);

        assertEquals(0, result.contentLengthBytes());
        assertEquals(100, result.performanceScore());
    }

    @Test
    void shouldCalculateMinimumScoreAndGradeCorrectly() {

        Map<String, List<String>> headers = Map.of(
                "Content-Length", List.of("3000000")
        );

        PerformanceAnalysisResponse result =
                service.analyze(1500, headers);

        assertEquals(40, result.performanceScore());
        assertEquals("D", result.grade());
    }

   @Test
void shouldNeverReturnScoreBelowZero() {

    Map<String, List<String>> headers = Map.of(
            "Content-Length", List.of("3000000")
    );

    PerformanceAnalysisResponse result =
            service.analyze(Long.MAX_VALUE, headers);

    assertTrue(result.performanceScore() >= 0);
    assertTrue(result.performanceScore() <= 100);
    assertEquals("D", result.grade());
}
}