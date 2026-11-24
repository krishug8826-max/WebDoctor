package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.ApiAnalysisResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiAnalysisServiceTest {

    @Test
    void shouldDetectDifferentApiPatterns() {

        ApiAnalysisService service = new ApiAnalysisService();

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>WebDoctor API Test</title>
                </head>
                <body>

                <script>
                    // Fetch API
                    // Wikipedia REST API
fetch("/w/rest.php/v1/page");
                    fetch("/api/users");

                    // Direct Axios
                    axios.get("/api/products");
                    axios.post("/api/orders");

                    // Axios Instance
                    const apiClient = axios.create({
                        baseURL: "/api"
                    });

                    apiClient.get("/customers");

                    // XMLHttpRequest
                    const xhr = new XMLHttpRequest();
                    xhr.open("GET", "/api/employees");

                    // jQuery AJAX
                    $.ajax({
                        url: "/api/categories",
                        method: "GET"
                    });

                    // Dynamic URL
                    const userId = 10;
                    fetch(`/api/users/${userId}`);

                    // GraphQL
                    const client = new ApolloClient({
                        uri: "/graphql"
                    });
                    fetch("/wp-json/wp/v2/posts");
fetch("/odata/Products");
fetch("/jsonrpc");
fetch("/rpc/users");
                </script>

                </body>
                </html>
                """;

        ApiAnalysisResponse result = service.analyze(
                "http://localhost:8080/test.html",
                html
        );

        System.out.println("\n========== WEBDOCTOR V4.1 TEST ==========");

        System.out.println(
                "Total Endpoints: " + result.totalEndpoints()
        );

        System.out.println(
                "Detected Patterns: " + result.apiPatternsDetected()
        );

        System.out.println(
                "Working Endpoints: " + result.workingEndpoints()
        );

        System.out.println(
                "Skipped Endpoints: " + result.skippedEndpoints()
        );

        result.findings().forEach(finding ->
                System.out.println(
                        finding.detectionSource()
                                + " | "
                                + finding.endpoint()
                                + " | "
                                + finding.status()
                                + " | "
                                + finding.confidence()
                )
        );

        System.out.println("=========================================\n");

        // Total detected endpoints
        assertEquals(13, result.totalEndpoints());

        // Different API patterns detected
        assertTrue(result.apiPatternsDetected() >= 10);

        // Localhost endpoints should not be actively scanned
        assertEquals(13, result.skippedEndpoints());

        // No endpoint should be reported as working
        assertEquals(0, result.workingEndpoints());

        // Dynamic endpoint detection
        assertTrue(
                result.findings().stream()
                        .anyMatch(f ->
                                f.detectionSource().equals("DYNAMIC_TEMPLATE")
                        )
        );

        // GraphQL endpoint detection
        assertTrue(
                result.findings().stream()
                        .anyMatch(f ->
                                f.detectionSource().equals("GRAPHQL")
                        )
        );
    }
    @Test
void shouldDetectJQueryShorthandApiCalls() {

    ApiAnalysisService service = new ApiAnalysisService();

    String html = """
            <html>
            <body>
                <script>
                    $.get("/api/search");
                    $.post("/api/login");
                </script>
            </body>
            </html>
            """;

    ApiAnalysisResponse result = service.analyze(
            "http://localhost:8080/test.html",
            html
    );

    assertEquals(2, result.totalEndpoints());
    assertEquals(2, result.skippedEndpoints());

    assertTrue(result.findings().stream()
            .anyMatch(f -> f.endpoint().contains("/api/search")));

    assertTrue(result.findings().stream()
            .anyMatch(f -> f.endpoint().contains("/api/login")));
}
@Test
void shouldIgnoreDocumentationUrlsButDetectRealApiEndpoints() {

    ApiAnalysisService service = new ApiAnalysisService();

    String html = """
            <html>
            <body>
                <script>
                    const docs = {
                        url: "https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API"
                    };

                    fetch("/api/users");
                </script>
            </body>
            </html>
            """;

    ApiAnalysisResponse result = service.analyze(
            "http://localhost:8080/test.html",
            html
    );

    assertEquals(1, result.totalEndpoints());

    assertTrue(result.findings().stream()
            .anyMatch(f -> f.endpoint().contains("/api/users")));

    assertFalse(result.findings().stream()
            .anyMatch(f -> f.endpoint().contains("developer.mozilla.org")));
}
}