package com.webdoctor.backend.codeanalyzer;

import com.webdoctor.backend.codeanalyzer.dto.CodeAnalysisResponse;
import com.webdoctor.backend.codeanalyzer.service.CodeAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeAnalysisServiceTest {

    private CodeAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new CodeAnalysisService();
    }

    @Test
    void shouldDetectAllEightRules() {

        String code = """
                eval("alert(1)");
                element.innerHTML = userInput;
                const api_key = "abcdefgh123456";
                document.write("Hello");
                if (a == b) {}
                debugger;
                console.log("test");
                var username = "admin";
                """;

        CodeAnalysisResponse response =
                service.analyze("javascript", code);

        assertEquals(8, response.totalIssues());
        assertEquals(2, response.errors());
        assertEquals(5, response.warnings());
        assertEquals(1, response.info());
    }

    @Test
    void shouldReturnPerfectScoreForCleanCode() {

        String code = """
                const name = "WebDoctor";
                let count = 10;
                """;

        CodeAnalysisResponse response =
                service.analyze("javascript", code);

        assertEquals(100, response.score());
        assertEquals(0, response.errors());
        assertEquals(0, response.warnings());
    }

    @Test
    void shouldCalculateScoreCorrectly() {

        String code = """
                eval("test");
                debugger;
                """;

        CodeAnalysisResponse response =
                service.analyze("js", code);

        assertEquals(78, response.score());
    }

    @Test
    void shouldRejectUnsupportedLanguage() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyze("python", "print('Hello')")
        );
    }

    @Test
    void shouldRejectEmptyCode() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyze("javascript", " ")
        );
    }

    @Test
    void shouldDetectCorrectLineNumber() {

        String code = """
                const name = "test";
                debugger;
                """;

        CodeAnalysisResponse response =
                service.analyze("javascript", code);

        assertEquals(2, response.findings().get(0).line());
    }
}