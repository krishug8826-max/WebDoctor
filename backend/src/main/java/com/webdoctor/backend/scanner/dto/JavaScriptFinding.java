package com.webdoctor.backend.scanner.dto;

public record JavaScriptFinding(

        String url,

        String scriptType,

        String linkType,

        int statusCode,

        String status,

        String message,

        String contentType

) {}