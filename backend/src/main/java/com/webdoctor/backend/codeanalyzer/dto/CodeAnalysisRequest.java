package com.webdoctor.backend.codeanalyzer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodeAnalysisRequest(

        @NotBlank(message = "Programming language is required")
        String language,

        @NotBlank(message = "Source code is required")
        @Size(max = 200000, message = "Code size must not exceed 200 KB")
        String code

) {}