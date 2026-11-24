package com.webdoctor.backend.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.webdoctor.backend.auth.dto.AuthResponse;
import com.webdoctor.backend.auth.dto.ChangePasswordRequest;
import com.webdoctor.backend.auth.dto.LoginRequest;
import com.webdoctor.backend.auth.dto.RegisterRequest;
import com.webdoctor.backend.auth.service.AuthService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import com.webdoctor.backend.auth.dto.ChangePasswordRequest;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {

        this.authService = authService;

        

    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {

        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                java.util.Map.of("message", exception.getMessage())
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {

        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                java.util.Map.of("message", exception.getMessage())
            );
        }
    }
@PostMapping("/change-password")
public ResponseEntity<?> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        @AuthenticationPrincipal Jwt jwt) {

    try {
        authService.changePassword(jwt.getSubject(), request);

        return ResponseEntity.ok(
            java.util.Map.of("message", "Password changed successfully")
        );

    } catch (IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(
            java.util.Map.of("message", exception.getMessage())
        );
    }
}
}
    
