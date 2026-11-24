package com.timegraph.controller;

import com.timegraph.auth.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(String username, String password) {}
    public record RegisterRequest(String username, String password, String fullName) {}

    public record AuthResponse(
            boolean success,
            boolean authenticated,
            String username,
            String fullName,
            String role,
            String message
    ) {}

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req, HttpSession session) {
        AuthService.User user = authService.authenticate(req.username(), req.password());
        if (user == null) {
            return ResponseEntity.badRequest().body(new AuthResponse(
                    false, false, null, null, null, "Invalid username or password"
            ));
        }

        session.setAttribute("USER", user.username());
        session.setAttribute("FULL_NAME", user.fullName());
        session.setAttribute("ROLE", user.role());

        return ResponseEntity.ok(new AuthResponse(
                true, true, user.username(), user.fullName(), user.role(), "Logged in as " + user.fullName()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req, HttpSession session) {
        try {
            AuthService.User user = authService.register(req.username(), req.password(), req.fullName());
            session.setAttribute("USER", user.username());
            session.setAttribute("FULL_NAME", user.fullName());
            session.setAttribute("ROLE", user.role());

            return ResponseEntity.ok(new AuthResponse(
                    true, true, user.username(), user.fullName(), user.role(), "Registered and logged in as " + user.fullName()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new AuthResponse(
                    false, false, null, null, null, e.getMessage()
            ));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(HttpSession session) {
        String username = (String) session.getAttribute("USER");
        if (username != null) {
            String fullName = (String) session.getAttribute("FULL_NAME");
            String role = (String) session.getAttribute("ROLE");
            return ResponseEntity.ok(new AuthResponse(
                    true, true, username, fullName, role, "Authenticated"
            ));
        }
        return ResponseEntity.ok(new AuthResponse(
                false, false, null, null, null, "Not logged in"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/demo-accounts")
    public ResponseEntity<List<Map<String, String>>> getDemoAccounts() {
        return ResponseEntity.ok(List.of(
                Map.of("username", "admin", "password", "admin123", "name", "Sarah Connor", "role", "Administrator"),
                Map.of("username", "analyst", "password", "analyst123", "name", "Alex Vance", "role", "Data Analyst")
        ));
    }
}
