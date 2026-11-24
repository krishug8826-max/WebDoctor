package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.SeoAnalysisResponse;
import com.webdoctor.backend.scanner.dto.SeoFinding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeoAnalysisServiceTest {

    private final SeoAnalysisService service = new SeoAnalysisService();

    private static final String VALID_TITLE =
            "Complete Website SEO Analysis Guide";

    private static final String VALID_DESCRIPTION =
            "Learn how to improve your website SEO with practical tips and techniques for better search visibility and performance.";

    private static final String VALID_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Complete Website SEO Analysis Guide</title>
                <meta name="description" content="Learn how to improve your website SEO with practical tips and techniques for better search visibility and performance.">
                <link rel="canonical" href="https://example.com/">
                <meta name="robots" content="index, follow">
            </head>
            <body>
                <h1>Website SEO Guide</h1>
                <img src="image.jpg" alt="Website SEO">
            </body>
            </html>
            """;

    // 1. All SEO elements are valid

    @Test
    void shouldGiveFullScoreWhenAllSeoElementsArePresent() {

        SeoAnalysisResponse response = service.analyze(VALID_HTML);

        assertEquals(100, response.seoScore());
        assertEquals("A", response.grade());

        assertEquals(1, response.imageCount());
        assertEquals(0, response.imagesWithoutAlt());

        assertEquals(VALID_TITLE, response.pageTitle());
        assertEquals(VALID_DESCRIPTION, response.metaDescription());

        assertEquals("SEO analysis completed.", response.message());
    }

    // 2. Empty HTML

    @Test
    void shouldDetectMissingSeoElementsInEmptyHtml() {

        SeoAnalysisResponse response = service.analyze("");

        assertEquals(40, response.seoScore());
        assertEquals("D", response.grade());

        assertEquals("", response.pageTitle());
        assertEquals("", response.metaDescription());
        assertEquals("", response.h1Heading());

        assertEquals(0, response.imageCount());
        assertEquals(0, response.imagesWithoutAlt());

        assertEquals(6, response.findings().size());
    }

    // 3. Missing page title

    @Test
    void shouldDetectMissingPageTitle() {

        String html = """
                <html>
                <head>
                    <meta name="description" content="%s">
                    <link rel="canonical" href="https://example.com">
                </head>
                <body>
                    <h1>SEO Guide</h1>
                </body>
                </html>
                """.formatted(VALID_DESCRIPTION);

        SeoAnalysisResponse response = service.analyze(html);

        assertEquals("", response.pageTitle());
        assertEquals(80, response.seoScore());
        assertEquals("MISSING", getFinding(response, "Page Title").status());
    }

    // 4. Short page title

    @Test
    void shouldApplyPenaltyForShortPageTitle() {

        String html = """
                <html>
                <head>
                    <title>Short Title</title>
                    <meta name="description" content="%s">
                    <link rel="canonical" href="https://example.com">
                </head>
                <body>
                    <h1>SEO Guide</h1>
                </body>
                </html>
                """.formatted(VALID_DESCRIPTION);

        SeoAnalysisResponse response = service.analyze(html);

        assertEquals(95, response.seoScore());
        assertEquals("A", response.grade());
    }

    // 5. Long page title

    @Test
    void shouldApplyPenaltyForLongPageTitle() {

        String longTitle = "A".repeat(70);

        String html = """
                <html>
                <head>
                    <title>%s</title>
                    <meta name="description" content="%s">
                    <link rel="canonical" href="https://example.com">
                </head>
                <body>
                    <h1>SEO Guide</h1>
                </body>
                </html>
                """.formatted(longTitle, VALID_DESCRIPTION);

        SeoAnalysisResponse response = service.analyze(html);

        assertEquals(95, response.seoScore());
    }

    // 6. Missing meta description

    @Test
    void shouldDetectMissingMetaDescription() {

        String html = """
                <html>
                <head>
                    <title>%s</title>
                    <link rel="canonical" href="https://example.com">
                </head>
                <body>
                    <h1>SEO Guide</h1>
                </body>
                </html>
                """.formatted(VALID_TITLE);

        SeoAnalysisResponse response = service.analyze(html);

        assertEquals("", response.metaDescription());
        assertEquals(85, response.seoScore());

        assertEquals(
                "MISSING",
                getFinding(response, "Meta Description").status()
        );
    }

    // 7. Short meta description

    @Test
    void shouldApplyPenaltyForShortMetaDescription() {

        String html = """
                <html>
                <head>
                    <title>%s</title>
                    <meta name="description" content="Short description">
                    <link rel="canonical" href="https://example.com">
                </head>
                <body>
                    <h1>SEO Guide</h1>
                </body>
                </html>
                """.formatted(VALID_TITLE);

        SeoAnalysisResponse response = service.analyze(html);

        assertEquals(95, response.seoScore());
    }

    // 8. Missing H1 heading

    @Test
    void shouldDetectMissingH1Heading() {

        String html = """
                <html>
                <head>
                    <title>%s</title>
                    <meta name="description" content="%s">
                    <link rel="canonical" href="https://example.com">
                </head>
                <body>
                    <p>Website content</p>
                </body>
                </html>
                """.formatted(VALID_TITLE, VALID_DESCRIPTION);

        SeoAnalysisResponse response = service.analyze(html);

        assertEquals("", response.h1Heading());
        assertEquals(85, response.seoScore());

        assertEquals(
                "MISSING",
                getFinding(response, "H1 Heading").status()
        );
    }

    // 9. Multiple H1 headings

    @Test
    void shouldApplyPenaltyForMultipleH1Headings() {

        String html = """
                <html>
                <head>
                    <title>%s</title>
                    <meta name="description" content="%s">
                    <link rel="canonical" href="https://example.com">
                </head>
                <body>
                    <h1>First Heading</h1>
                    <h1>Second Heading</h1>
                </body>
                </html>
                """.formatted(VALID_TITLE, VALID_DESCRIPTION);

        SeoAnalysisResponse response = service.analyze(html);

        assertEquals(95, response.seoScore());
        assertEquals("First Heading", response.h1Heading());
    }

    // 10. Images without alt attributes

    @Test
    void shouldDetectImagesWithoutAltAttributes() {

        String html = """
                <html>
                <head>
                    <title>%s</title>
                    <meta name="description" content="%s">
                    <link rel="canonical" href="https://example.com">
                </head>
                <body>
                    <h1>SEO Guide</h1>
                    <img src="image1.jpg">
                    <img src="image2.jpg">
                </body>
                </html>
                """.formatted(VALID_TITLE, VALID_DESCRIPTION);

        SeoAnalysisResponse response = service.analyze(html);

        assertEquals(2, response.imageCount());
        assertEquals(2, response.imagesWithoutAlt());

        assertEquals(94, response.seoScore());

        assertEquals(
                "WARNING",
                getFinding(response, "Image Alt Attributes").status()
        );
    }

    // 11. Image alt penalty limit

    @Test
    void shouldLimitImageAltPenaltyTo15Points() {

        StringBuilder images = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            images.append("<img src='image").append(i).append(".jpg'>");
        }

        String html = """
                <html>
                <head>
                    <title>%s</title>
                    <meta name="description" content="%s">
                    <link rel="canonical" href="https://example.com">
                </head>
                <body>
                    <h1>SEO Guide</h1>
                    %s
                </body>
                </html>
                """.formatted(VALID_TITLE, VALID_DESCRIPTION, images);

        SeoAnalysisResponse response = service.analyze(html);

        assertEquals(6, response.imageCount());
        assertEquals(6, response.imagesWithoutAlt());

        assertEquals(85, response.seoScore());
        assertEquals("B", response.grade());
    }

    // 12. Missing canonical URL

    @Test
    void shouldDetectMissingCanonicalUrl() {

        String html = """
                <html>
                <head>
                    <title>%s</title>
                    <meta name="description" content="%s">
                </head>
                <body>
                    <h1>SEO Guide</h1>
                </body>
                </html>
                """.formatted(VALID_TITLE, VALID_DESCRIPTION);

        SeoAnalysisResponse response = service.analyze(html);

        assertEquals(90, response.seoScore());

        assertEquals(
                "MISSING",
                getFinding(response, "Canonical URL").status()
        );
    }

    // 13. Robots meta tag

    @Test
    void shouldDetectRobotsMetaTagWithoutChangingScore() {

        SeoAnalysisResponse response = service.analyze(VALID_HTML);

        SeoFinding robotsFinding = getFinding(response, "Robots Meta Tag");

        assertEquals("FOUND", robotsFinding.status());
        assertEquals(100, response.seoScore());
    }

    private SeoFinding getFinding(
            SeoAnalysisResponse response,
            String findingName
    ) {

        return response.findings()
                .stream()
                .filter(finding -> finding.name().equals(findingName))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "Finding not found: " + findingName
                        )
                );
    }
}