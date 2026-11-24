package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.BrokenLinkAnalysisResponse;
import com.webdoctor.backend.scanner.dto.BrokenLinkFinding;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BrokenLinkAnalysisServiceTest {

    private BrokenLinkAnalysisService service;

    private HttpServer server;

    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/working", exchange -> {

            exchange.getResponseHeaders()
                    .set("Content-Type", "text/plain");

            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {

                exchange.sendResponseHeaders(200, -1);

            } else {

                byte[] body = "Working".getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(200, body.length);

                exchange.getResponseBody().write(body);
            }

            exchange.close();
        });

        server.createContext("/broken", exchange -> {

            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        server.createContext("/server-error", exchange -> {

            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        server.createContext("/redirect", exchange -> {

            exchange.getResponseHeaders().set("Location", "/working");

            exchange.sendResponseHeaders(302, -1);

            exchange.close();
        });

        server.createContext("/redirect-no-location", exchange -> {

            exchange.sendResponseHeaders(302, -1);

            exchange.close();
        });

        server.createContext("/loop-a", exchange -> {

            exchange.getResponseHeaders().set("Location", "/loop-b");

            exchange.sendResponseHeaders(302, -1);

            exchange.close();
        });

        server.createContext("/loop-b", exchange -> {

            exchange.getResponseHeaders().set("Location", "/loop-a");

            exchange.sendResponseHeaders(302, -1);

            exchange.close();
        });

        server.createContext("/head-not-supported", exchange -> {

            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {

                exchange.sendResponseHeaders(405, -1);

            } else {

                byte[] body = "GET works".getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(200, body.length);

                exchange.getResponseBody().write(body);
            }

            exchange.close();
        });

        server.start();

        baseUrl = "http://127.0.0.1:"
                + server.getAddress().getPort();

        HttpClient testClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        /*
         * Test-only address checker.
         *
         * This allows requests to the controlled local HTTP server.
         * Production constructor still uses real public-IP validation.
         */
        service = new BrokenLinkAnalysisService(
                testClient,
                host -> true
        );
    }

    @AfterEach
    void tearDown() {

        if (server != null) {
            server.stop(0);
        }
    }

    private BrokenLinkAnalysisResponse analyze(String html) {

        return service.analyze(baseUrl + "/page", html);
    }

    private BrokenLinkFinding find(
            BrokenLinkAnalysisResponse response,
            String path
    ) {

        return response.findings()
                .stream()
                .filter(finding -> finding.url().endsWith(path))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Finding not found: " + path)
                );
    }

    @Test
    void shouldDetectWorkingLink() {

        BrokenLinkAnalysisResponse response =
                analyze("<a href='/working'>Working</a>");

        assertEquals(1, response.totalLinks());
        assertEquals(1, response.workingLinks());
        assertEquals(0, response.brokenLinks());

        assertEquals("WORKING", find(response, "/working").status());

        assertEquals(
                200,
                find(response, "/working").statusCode()
        );

        assertEquals(
                "INTERNAL",
                find(response, "/working").linkType()
        );
    }

    @Test
    void shouldDetectBroken404Link() {

        BrokenLinkAnalysisResponse response =
                analyze("<a href='/broken'>Broken</a>");

        BrokenLinkFinding finding = find(response, "/broken");

        assertEquals(1, response.brokenLinks());

        assertEquals("BROKEN", finding.status());

        assertEquals(404, finding.statusCode());
    }

    @Test
    void shouldDetectServerError500() {

        BrokenLinkAnalysisResponse response =
                analyze("<a href='/server-error'>Server Error</a>");

        BrokenLinkFinding finding = find(response, "/server-error");

        assertEquals(1, response.brokenLinks());

        assertEquals("BROKEN", finding.status());

        assertEquals(500, finding.statusCode());
    }

    @Test
    void shouldFollowRedirectSuccessfully() {

        BrokenLinkAnalysisResponse response =
                analyze("<a href='/redirect'>Redirect</a>");

        BrokenLinkFinding finding = find(response, "/redirect");

        assertEquals(1, response.redirectedLinks());

        assertEquals("REDIRECT", finding.status());

        assertEquals(200, finding.statusCode());

        assertEquals(1, finding.redirectCount());

        assertTrue(finding.finalUrl().endsWith("/working"));
    }

    @Test
    void shouldDetectRedirectWithoutLocation() {

        BrokenLinkAnalysisResponse response =
                analyze("<a href='/redirect-no-location'>Redirect</a>");

        BrokenLinkFinding finding =
                find(response, "/redirect-no-location");

        assertEquals(1, response.brokenLinks());

        assertEquals("BROKEN", finding.status());

        assertEquals(302, finding.statusCode());

        assertEquals(
                "Redirect location is missing",
                finding.message()
        );
    }

    @Test
    void shouldDetectRedirectLoop() {

        BrokenLinkAnalysisResponse response =
                analyze("<a href='/loop-a'>Loop</a>");

        BrokenLinkFinding finding = find(response, "/loop-a");

        assertEquals(1, response.brokenLinks());

        assertEquals("BROKEN", finding.status());

        assertEquals(
                "Redirect loop detected",
                finding.message()
        );
    }

    @Test
    void shouldFallbackToGetWhenHeadIsNotSupported() {

        BrokenLinkAnalysisResponse response =
                analyze("<a href='/head-not-supported'>GET fallback</a>");

        BrokenLinkFinding finding =
                find(response, "/head-not-supported");

        assertEquals(1, response.workingLinks());

        assertEquals("WORKING", finding.status());

        assertEquals(200, finding.statusCode());
    }
@Test
void shouldSkipLocalhostAddresses() {

    BrokenLinkAnalysisService productionService =
            new BrokenLinkAnalysisService();

    BrokenLinkAnalysisResponse response =
            productionService.analyze(
                    baseUrl + "/page",
                    "<a href='http://localhost:8080/api/users'>API</a>"
            );

    assertEquals(1, response.totalLinks());
    assertEquals(1, response.skippedLinks());
    assertEquals(0, response.brokenLinks());

    assertEquals(
            "SKIPPED",
            response.findings().get(0).status()
    );
}

   @Test
void shouldSkipPrivateIpAddresses() {

    BrokenLinkAnalysisService productionService =
            new BrokenLinkAnalysisService();

    BrokenLinkAnalysisResponse response =
            productionService.analyze(
                    baseUrl + "/page",
                    "<a href='http://127.0.0.1:8080/private'>Private</a>"
            );

    assertEquals(1, response.totalLinks());
    assertEquals(1, response.skippedLinks());

    assertEquals(
            "SKIPPED",
            find(response, "/private").status()
    );
}

    @Test
    void shouldIgnoreMailtoTelJavascriptAndFragmentLinks() {

        String html = """
                <a href="mailto:test@example.com">Email</a>
                <a href="tel:1234567890">Phone</a>
                <a href="javascript:void(0)">JavaScript</a>
                <a href="#section">Section</a>
                <a href="data:text/plain,test">Data</a>
                """;

        BrokenLinkAnalysisResponse response = analyze(html);

        assertEquals(0, response.totalLinks());

        assertTrue(response.findings().isEmpty());
    }

    @Test
    void shouldRemoveDuplicateLinks() {

        String html = """
                <a href="/working">First</a>
                <a href="/working">Second</a>
                <a href="/working">Third</a>
                """;

        BrokenLinkAnalysisResponse response = analyze(html);

        assertEquals(1, response.totalLinks());

        assertEquals(1, response.findings().size());

        assertEquals(1, response.workingLinks());
    }

    @Test
    void shouldDetectInvalidUrlInput() {

        BrokenLinkAnalysisResponse response =
                service.analyze(
                        "invalid-url",
                        "<a href='/working'>Link</a>"
                );

        assertEquals(0, response.totalLinks());

        assertEquals(0, response.workingLinks());

        assertEquals(0, response.brokenLinks());

        assertEquals(0, response.redirectedLinks());

        assertEquals(0, response.skippedLinks());

        assertEquals(
                "Broken link analysis failed",
                response.message()
        );
    }

    @Test
    void shouldRespectMaximumLinkLimit() {

        StringBuilder html = new StringBuilder();

        for (int i = 0; i < 30; i++) {

            html.append("<a href='/working?item=")
                    .append(i)
                    .append("'>Link</a>");
        }

        BrokenLinkAnalysisResponse response = analyze(html.toString());

        assertEquals(30, response.totalLinks());

        assertEquals(25, response.findings().size());

        assertEquals(25, response.workingLinks());

        assertEquals(5, response.skippedLinks());
    }

    @Test
    void shouldClassifyRelativeLinksAsInternal() {

        BrokenLinkAnalysisResponse response =
                analyze("<a href='/working'>Internal</a>");

        BrokenLinkFinding finding = find(response, "/working");

        assertEquals("INTERNAL", finding.linkType());

        assertEquals("WORKING", finding.status());
    }

    @Test
    void shouldReturnSuccessfulAnalysisMessage() {

        BrokenLinkAnalysisResponse response = analyze("");

        assertEquals(
                "Advanced link analysis completed successfully",
                response.message()
        );

        assertEquals(0, response.totalLinks());
    }

    @Test
    void shouldDetectAreaHrefLinks() {

        BrokenLinkAnalysisResponse response =
                analyze("<area href='/working' alt='Working area'>");

        assertEquals(1, response.totalLinks());

        assertEquals(1, response.findings().size());

        assertEquals(1, response.workingLinks());
    }
}