package com.webdoctor.backend.auth.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webdoctor.backend.auth.dto.AuthResponse;
import com.webdoctor.backend.auth.dto.LoginRequest;
import com.webdoctor.backend.auth.dto.RegisterRequest;
import com.webdoctor.backend.auth.entity.AppUser;
import com.webdoctor.backend.auth.repository.AppUserRepository;
import com.webdoctor.backend.auth.dto.ChangePasswordRequest;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        AppUser user = new AppUser(
            request.getName().trim(),
            email,
            passwordEncoder.encode(request.getPassword())
        );

        AppUser savedUser = userRepository.save(user);

        String token = jwtService.generateToken(
            savedUser.getId(),
            savedUser.getEmail(),
            savedUser.getName()
        );

        return new AuthResponse(
            token,
            savedUser.getId(),
            savedUser.getName(),
            savedUser.getEmail()
        );
    }
@Transactional
public void changePassword(String email, ChangePasswordRequest request) {

    AppUser user = userRepository.findByEmail(
        email.trim().toLowerCase(Locale.ROOT)
    ).orElseThrow(() ->
        new IllegalArgumentException("User account not found")
    );

    if (!passwordEncoder.matches(
            request.getCurrentPassword(),
            user.getPasswordHash())) {
        throw new IllegalArgumentException("Current password is incorrect");
    }

    if (!request.getNewPassword().equals(
            request.getConfirmNewPassword())) {
        throw new IllegalArgumentException("New passwords do not match");
    }

    if (passwordEncoder.matches(
            request.getNewPassword(),
            user.getPasswordHash())) {
        throw new IllegalArgumentException(
            "New password must be different from current password"
        );
    }

    user.setPasswordHash(
        passwordEncoder.encode(request.getNewPassword())
    );

    userRepository.save(user);
}
    public AuthResponse login(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        AppUser user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = jwtService.generateToken(
            user.getId(),
            user.getEmail(),
            user.getName()
        );

        return new AuthResponse(
            token,
            user.getId(),
            user.getName(),
            user.getEmail()
        );
    }
}