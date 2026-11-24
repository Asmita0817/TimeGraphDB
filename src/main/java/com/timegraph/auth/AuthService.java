package com.timegraph.auth;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    public record User(String username, String password, String fullName, String role) {}

    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    public AuthService() {
        // Pre-seed demo collaboration accounts
        users.put("admin", new User("admin", "admin123", "Sarah Connor", "Administrator"));
        users.put("analyst", new User("analyst", "analyst123", "Alex Vance", "Data Analyst"));
    }

    public User authenticate(String username, String password) {
        if (username == null || password == null) return null;
        User user = users.get(username.trim().toLowerCase());
        if (user != null && user.password().equals(password.trim())) {
            return user;
        }
        return null;
    }

    public synchronized User register(String username, String password, String fullName) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (password == null || password.length() < 3) {
            throw new IllegalArgumentException("Password must be at least 3 characters");
        }
        String key = username.trim().toLowerCase();
        if (users.containsKey(key)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken");
        }
        String name = (fullName != null && !fullName.isBlank()) ? fullName.trim() : username.trim();
        User user = new User(key, password.trim(), name, "Contributor");
        users.put(key, user);
        return user;
    }

    public User getUser(String username) {
        if (username == null) return null;
        return users.get(username.trim().toLowerCase());
    }

    public Collection<User> getAllUsers() {
        return users.values();
    }
}
