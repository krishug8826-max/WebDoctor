package com.webdoctor.backend.codeanalyzer.controller;

import com.webdoctor.backend.codeanalyzer.dto.CodeAnalysisRequest;
import com.webdoctor.backend.codeanalyzer.dto.CodeAnalysisResponse;
import com.webdoctor.backend.codeanalyzer.service.CodeAnalysisService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/code-analyzer")
@CrossOrigin(origins = "http://localhost:5173")
public class CodeAnalysisController {

    private final CodeAnalysisService codeAnalysisService;

    public CodeAnalysisController(CodeAnalysisService codeAnalysisService) {
        this.codeAnalysisService = codeAnalysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeCode(
            @Valid @RequestBody CodeAnalysisRequest request) {

        try {
            CodeAnalysisResponse response =
                    codeAnalysisService.analyze(
                            request.language(),
                            request.code()
                    );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}