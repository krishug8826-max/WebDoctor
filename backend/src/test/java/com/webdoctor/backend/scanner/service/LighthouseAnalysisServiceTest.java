package com.webdoctor.backend.scanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webdoctor.backend.scanner.dto.LighthouseAnalysisResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LighthouseAnalysisServiceTest {

    @TempDir
    Path tempDir;

    private Path createRunner(String script) throws Exception {

        Path runner = tempDir.resolve("lighthouse-runner.mjs");

        Files.writeString(runner, script);

        return runner;
    }

    private LighthouseAnalysisService createService(
            String script,
            long timeoutSeconds
    ) throws Exception {

        return new LighthouseAnalysisService(
                new ObjectMapper(),
                createRunner(script),
                timeoutSeconds
        );
    }

    @Test
    void shouldCompleteMobileAndDesktopAudits() throws Exception {

        String script = """
                const strategy = process.argv[3];

                console.log(JSON.stringify({
                    status: "COMPLETED",
                    strategy: strategy,
                    performanceScore: 95
                }));
                """;

        LighthouseAnalysisService service =
                createService(script, 5);

        LighthouseAnalysisResponse result =
                service.analyze("https://example.com");

        assertEquals("COMPLETED", result.status());

        assertEquals("COMPLETED", result.mobile().status());
        assertEquals("COMPLETED", result.desktop().status());

        assertEquals(95, result.mobile().performanceScore());
        assertEquals(95, result.desktop().performanceScore());
    }

    @Test
    void shouldReturnPartialWhenDesktopAuditFails() throws Exception {

        String script = """
                const strategy = process.argv[3];

                if (strategy === "desktop") {
                    console.error("Desktop audit failed");
                    process.exit(1);
                }

                console.log(JSON.stringify({
                    status: "COMPLETED",
                    strategy: strategy,
                    performanceScore: 90
                }));
                """;

        LighthouseAnalysisService service =
                createService(script, 5);

        LighthouseAnalysisResponse result =
                service.analyze("https://example.com");

        assertEquals("PARTIAL", result.status());

        assertEquals("COMPLETED", result.mobile().status());
        assertEquals("FAILED", result.desktop().status());
    }

    @Test
    void shouldReturnFailedWhenBothAuditsFail() throws Exception {

        String script = """
                console.error("Simulated Lighthouse failure");
                process.exit(1);
                """;

        LighthouseAnalysisService service =
                createService(script, 5);

        LighthouseAnalysisResponse result =
                service.analyze("https://example.com");

        assertEquals("FAILED", result.status());

        assertEquals("FAILED", result.mobile().status());
        assertEquals("FAILED", result.desktop().status());
    }

    @Test
    void shouldHandleInvalidJsonResponse() throws Exception {

        String script = """
                console.log("This is not valid JSON");
                """;

        LighthouseAnalysisService service =
                createService(script, 5);

        LighthouseAnalysisResponse result =
                service.analyze("https://example.com");

        assertEquals("FAILED", result.status());

        assertEquals("FAILED", result.mobile().status());
        assertEquals("FAILED", result.desktop().status());
    }

    @Test
    void shouldHandleJavaProcessTimeout() throws Exception {

        String script = """
                setTimeout(() => {
                    console.log("Completed");
                }, 10000);
                """;

        LighthouseAnalysisService service =
                createService(script, 1);

        LighthouseAnalysisResponse result =
                service.analyze("https://example.com");

        assertEquals("FAILED", result.status());

        assertEquals("FAILED", result.mobile().status());
        assertEquals("FAILED", result.desktop().status());
    }
}
