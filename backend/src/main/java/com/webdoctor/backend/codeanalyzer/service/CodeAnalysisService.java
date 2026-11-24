package com.webdoctor.backend.codeanalyzer.service;

import com.webdoctor.backend.codeanalyzer.dto.CodeAnalysisResponse;
import com.webdoctor.backend.codeanalyzer.dto.CodeFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeAnalysisService {

    private record Rule(
            String id,
            String severity,
            String title,
            String message,
            String suggestion,
            Pattern pattern
    ) {}

    private static final List<Rule> RULES = List.of(

            new Rule(
                    "JS001", "ERROR",
                    "Unsafe eval() usage",
                    "eval() can execute arbitrary JavaScript and create security risks.",
                    "Avoid eval(). Use safer alternatives such as JSON.parse() where appropriate.",
                    Pattern.compile("\\beval\\s*\\(")
            ),

            new Rule(
                    "JS002", "ERROR",
                    "Unsafe HTML insertion",
                    "innerHTML can expose your application to cross-site scripting (XSS).",
                    "Prefer textContent or sanitize untrusted HTML before insertion.",
                    Pattern.compile("\\.innerHTML\\s*=")
            ),

            new Rule(
                    "JS003", "WARNING",
                    "Hardcoded secret detected",
                    "A possible API key, password, token or secret is hardcoded.",
                    "Move secrets to a secure backend environment variable. Never expose private secrets in frontend code.",
                    Pattern.compile("(?i)(api[_-]?key|secret|password|token)\\s*[:=]\\s*['\"][^'\"]{8,}['\"]")
            ),

            new Rule(
                    "JS004", "WARNING",
                    "document.write() usage",
                    "document.write() can overwrite the document and introduce security issues.",
                    "Use DOM methods such as createElement() and appendChild().",
                    Pattern.compile("\\bdocument\\s*\\.\\s*write\\s*\\(")
            ),

            new Rule(
                    "JS005", "WARNING",
                    "Loose equality operator",
                    "Loose equality may produce unexpected type conversions.",
                    "Use === or !== when strict comparison is intended.",
                    Pattern.compile("(?<![=!])==(?!=)")
            ),

            new Rule(
                    "JS006", "WARNING",
                    "Debugger statement found",
                    "A debugger statement can interrupt execution during development.",
                    "Remove debugger statements before production deployment.",
                    Pattern.compile("\\bdebugger\\s*;?")
            ),

            new Rule(
                    "JS007", "INFO",
                    "console.log() statement",
                    "Console logging may expose unnecessary debugging information.",
                    "Remove unnecessary console.log() statements before production.",
                    Pattern.compile("\\bconsole\\s*\\.\\s*log\\s*\\(")
            ),

            new Rule(
                    "JS008", "WARNING",
                    "var declaration found",
                    "var has function scope and can cause unexpected behavior.",
                    "Prefer const or let for variable declarations.",
                    Pattern.compile("\\bvar\\s+[a-zA-Z_$][\\w$]*")
            )
    );

    public CodeAnalysisResponse analyze(String language, String code) {

        if (language == null ||
                !(language.equalsIgnoreCase("javascript")
                || language.equalsIgnoreCase("js"))) {
            throw new IllegalArgumentException(
                    "Currently only JavaScript (js) is supported."
            );
        }

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Source code cannot be empty.");
        }

        List<CodeFinding> findings = new ArrayList<>();

        for (Rule rule : RULES) {

            Matcher matcher = rule.pattern().matcher(code);

            while (matcher.find()) {

                int line = 1;

                for (int i = 0; i < matcher.start(); i++) {
                    if (code.charAt(i) == '\n') {
                        line++;
                    }
                }

                findings.add(new CodeFinding(
                        rule.id(),
                        rule.severity(),
                        rule.title(),
                        rule.message(),
                        line,
                        rule.suggestion()
                ));
            }
        }

        int errors = (int) findings.stream()
                .filter(f -> f.severity().equals("ERROR"))
                .count();

        int warnings = (int) findings.stream()
                .filter(f -> f.severity().equals("WARNING"))
                .count();

        int info = (int) findings.stream()
                .filter(f -> f.severity().equals("INFO"))
                .count();

        int score = Math.max(
                0,
                100 - (errors * 15) - (warnings * 7) - (info * 2)
        );

        return new CodeAnalysisResponse(
                "JavaScript",
                score,
                findings.size(),
                errors,
                warnings,
                info,
                findings
        );
    }
}