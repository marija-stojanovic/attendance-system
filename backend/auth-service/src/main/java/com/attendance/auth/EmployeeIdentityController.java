package com.attendance.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/employees")
public class EmployeeIdentityController {
    private final LocalUserRepository users;
    private final byte[] internalKey;

    public EmployeeIdentityController(LocalUserRepository users,
            @Value("${internal.service-key}") String internalKey) {
        this.users = users;
        this.internalKey = internalKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/by-email")
    public EmployeeIdentity findByEmail(@RequestParam String email,
            @RequestHeader("X-Internal-Key") String suppliedKey) {
        if (!MessageDigest.isEqual(internalKey, suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Interni pristup nije dozvoljen.");
        }
        LocalUser user = users.findByEmailIgnoreCase(email)
                .filter(candidate -> "EMPLOYEE".equals(candidate.getRole()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zaposleni nije pronađen."));
        return new EmployeeIdentity(user.getId(), user.getEmail(), user.getUsername(), user.getDisplayName());
    }

    public record EmployeeIdentity(UUID id, String email, String username, String displayName) {}
}
