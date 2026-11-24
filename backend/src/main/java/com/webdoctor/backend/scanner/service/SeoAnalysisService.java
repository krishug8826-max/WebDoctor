package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.SeoAnalysisResponse;
import com.webdoctor.backend.scanner.dto.SeoFinding;

import org.springframework.stereotype.Service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

@Service
public class SeoAnalysisService {

    public SeoAnalysisResponse analyze(String html) {

        Document document = Jsoup.parse(html);

        List<SeoFinding> findings = new ArrayList<>();

        int score = 100;

        // 1. Page Title

        String title = document.title();

        if (title.isBlank()) {

            findings.add(new SeoFinding(
                    "Page Title",
                    "MISSING",
                    "Page title is missing.",
                    "Add a unique and descriptive title."
            ));

            score -= 20;

        } else {

            findings.add(new SeoFinding(
                    "Page Title",
                    "FOUND",
                    "Page title detected: " + title,
                    "Keep the title descriptive and relevant to the page."
            ));

            if (title.length() < 30 || title.length() > 60) {
                score -= 5;
            }
        }

        // 2. Meta Description

        Element metaDescription = document.selectFirst(
                "meta[name=description]"
        );

        String description = metaDescription == null
                ? ""
                : metaDescription.attr("content").trim();

        if (description.isBlank()) {

            findings.add(new SeoFinding(
                    "Meta Description",
                    "MISSING",
                    "Meta description is missing.",
                    "Add a meaningful meta description."
            ));

            score -= 15;

        } else {

            findings.add(new SeoFinding(
                    "Meta Description",
                    "FOUND",
                    "Meta description detected.",
                    "Keep it relevant and useful for search visitors."
            ));

            if (description.length() < 70 ||
                    description.length() > 160) {
                score -= 5;
            }
        }

        // 3. H1 Heading

        Elements h1Elements = document.select("h1");

        String h1 = h1Elements.isEmpty()
                ? ""
                : h1Elements.first().text();

        if (h1Elements.isEmpty()) {

            findings.add(new SeoFinding(
                    "H1 Heading",
                    "MISSING",
                    "No H1 heading found.",
                    "Add a clear main heading to the page."
            ));

            score -= 15;

        } else {

            findings.add(new SeoFinding(
                    "H1 Heading",
                    "FOUND",
                    "H1 heading detected: " + h1,
                    "Use one clear primary heading where appropriate."
            ));

            if (h1Elements.size() > 1) {
                score -= 5;
            }
        }

        // 4. Image Alt Attributes

        Elements images = document.select("img");

        int imageCount = images.size();
        int imagesWithoutAlt = 0;

        for (Element image : images) {

            if (!image.hasAttr("alt")) {
                imagesWithoutAlt++;
            }
        }

        if (imagesWithoutAlt > 0) {

            findings.add(new SeoFinding(
                    "Image Alt Attributes",
                    "WARNING",
                    imagesWithoutAlt + " images do not have an alt attribute.",
                    "Add appropriate alt attributes to informative images."
            ));

            score -= Math.min(15, imagesWithoutAlt * 3);

        } else {

            findings.add(new SeoFinding(
                    "Image Alt Attributes",
                    "GOOD",
                    "All detected images have an alt attribute.",
                    "Keep image descriptions meaningful."
            ));
        }

        // 5. Canonical URL

        Element canonical = document.selectFirst(
                "link[rel=canonical]"
        );

        if (canonical == null ||
                canonical.attr("href").isBlank()) {

            findings.add(new SeoFinding(
                    "Canonical URL",
                    "MISSING",
                    "Canonical URL was not detected.",
                    "Add a canonical link when appropriate to identify the preferred URL."
            ));

            score -= 10;

        } else {

            findings.add(new SeoFinding(
                    "Canonical URL",
                    "FOUND",
                    "Canonical URL: " + canonical.attr("href"),
                    "Ensure the canonical URL points to the intended preferred page."
            ));
        }

        // 6. Robots Meta Tag

        Element robots = document.selectFirst(
                "meta[name=robots]"
        );

        findings.add(new SeoFinding(
                "Robots Meta Tag",
                robots == null ? "NOT_FOUND" : "FOUND",
                robots == null
                        ? "No robots meta tag detected."
                        : "Robots directive: " + robots.attr("content"),
                "Check that indexing directives match your search visibility requirements."
        ));

        // Final Score

        score = Math.max(0, Math.min(100, score));

        String grade;

        if (score >= 90) {
            grade = "A";
        } else if (score >= 75) {
            grade = "B";
        } else if (score >= 60) {
            grade = "C";
        } else if (score >= 40) {
            grade = "D";
        } else {
            grade = "F";
        }

        return new SeoAnalysisResponse(
                score,
                grade,
                title,
                description,
                h1,
                imageCount,
                imagesWithoutAlt,
                findings,
                "SEO analysis completed."
        );
    }
}