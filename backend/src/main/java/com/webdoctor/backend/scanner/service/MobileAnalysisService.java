package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.MobileAnalysisResponse;
import com.webdoctor.backend.scanner.dto.MobileFinding;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MobileAnalysisService {

    public MobileAnalysisResponse analyze(String html) {

        List<MobileFinding> findings = new ArrayList<>();

        int score = 100;

        boolean viewportConfigured = false;
        boolean responsiveViewport = false;

        int imageCount = 0;
        int imagesWithFixedWidth = 0;
        int fixedWidthElements = 0;
        int tableCount = 0;

        try {

            Document document = Jsoup.parse(html);

            // 1. Viewport Analysis

            Element viewport = document.selectFirst(
                    "meta[name=viewport]"
            );

            if (viewport != null) {

                viewportConfigured = true;

                String viewportContent =
                        viewport.attr("content").toLowerCase();

                if (viewportContent.contains("width=device-width")) {

                    responsiveViewport = true;

                    findings.add(new MobileFinding(
                            "Responsive Viewport",
                            "PASS",
                            "Website uses a responsive viewport configuration.",
                            "No action required."
                    ));

                } else {

                    score -= 15;

                    findings.add(new MobileFinding(
                            "Responsive Viewport",
                            "WARNING",
                            "Viewport exists but width=device-width was not detected.",
                            "Use width=device-width, initial-scale=1 in the viewport meta tag."
                    ));
                }

            } else {

                score -= 25;

                findings.add(new MobileFinding(
                        "Viewport Configuration",
                        "FAIL",
                        "Viewport meta tag is missing.",
                        "Add <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">."
                ));
            }

            // 2. Image Analysis

            Elements images = document.select("img");

            imageCount = images.size();

            for (Element image : images) {

                String width = image.attr("width").trim();

                if (width.matches("\\d+")) {

                    int imageWidth = Integer.parseInt(width);

                    if (imageWidth > 480) {

                        imagesWithFixedWidth++;
                    }
                }

                String style = image.attr("style");

                if (hasLargeFixedWidth(style)) {

                    imagesWithFixedWidth++;
                }
            }

            if (imagesWithFixedWidth > 0) {

                score -= Math.min(
                        20,
                        imagesWithFixedWidth * 5
                );

                findings.add(new MobileFinding(
                        "Image Sizing",
                        "WARNING",
                        imagesWithFixedWidth
                                + " images contain potentially large fixed widths.",
                        "Use max-width: 100%; height: auto for responsive images."
                ));

            } else {

                findings.add(new MobileFinding(
                        "Image Sizing",
                        "PASS",
                        "No large fixed-width image dimensions were detected.",
                        "Continue using responsive image sizing."
                ));
            }

            // 3. Fixed Width Element Analysis

            Elements elements = document.select("[style]");

            for (Element element : elements) {

                if (!element.tagName().equals("img")
                        && hasLargeFixedWidth(element.attr("style"))) {

                    fixedWidthElements++;
                }
            }

            if (fixedWidthElements > 0) {

                score -= Math.min(
                        20,
                        fixedWidthElements * 5
                );

                findings.add(new MobileFinding(
                        "Fixed Width Elements",
                        "WARNING",
                        fixedWidthElements
                                + " elements contain potentially large fixed widths.",
                        "Prefer max-width: 100%, percentages, flexbox or CSS grid."
                ));

            } else {

                findings.add(new MobileFinding(
                        "Fixed Width Elements",
                        "PASS",
                        "No large fixed-width inline styles were detected.",
                        "Continue using flexible layouts."
                ));
            }

            // 4. Table Analysis

            Elements tables = document.select("table");

            tableCount = tables.size();

            if (tableCount > 0) {

                findings.add(new MobileFinding(
                        "HTML Tables",
                        "WARNING",
                        tableCount
                                + " HTML tables detected. Wide tables may overflow on mobile.",
                        "Use responsive table wrappers with horizontal scrolling when needed."
                ));

            } else {

                findings.add(new MobileFinding(
                        "HTML Tables",
                        "PASS",
                        "No HTML tables were detected.",
                        "No action required."
                ));
            }

            // Final Score

            score = Math.max(0, Math.min(100, score));

            String grade = calculateGrade(score);

            return new MobileAnalysisResponse(
                    score,
                    grade,
                    viewportConfigured,
                    responsiveViewport,
                    imageCount,
                    imagesWithFixedWidth,
                    fixedWidthElements,
                    tableCount,
                    findings,
                    "Mobile responsiveness analysis completed successfully"
            );

        } catch (Exception e) {

            return new MobileAnalysisResponse(
                    0,
                    "N/A",
                    false,
                    false,
                    0,
                    0,
                    0,
                    0,
                    findings,
                    "Mobile responsiveness analysis failed"
            );
        }
    }

    private boolean hasLargeFixedWidth(String style) {

        if (style == null || style.isBlank()) {
            return false;
        }

        Pattern pattern = Pattern.compile(
                "width\\s*:\\s*(\\d+)px",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(style);

        while (matcher.find()) {

            try {

                int width = Integer.parseInt(matcher.group(1));

                if (width > 480) {
                    return true;
                }

            } catch (NumberFormatException ignored) {
                // Ignore invalid width
            }
        }

        return false;
    }

    private String calculateGrade(int score) {

        if (score >= 90) {
            return "A";
        }

        if (score >= 75) {
            return "B";
        }

        if (score >= 60) {
            return "C";
        }

        if (score >= 40) {
            return "D";
        }

        return "F";
    }
}