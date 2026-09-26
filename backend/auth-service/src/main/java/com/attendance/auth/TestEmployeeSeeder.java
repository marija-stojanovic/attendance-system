package com.attendance.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "authentication.test-employee-enabled", havingValue = "true")
public class TestEmployeeSeeder implements ApplicationRunner {
    private final LocalUserRepository users;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;
    private final String email;
    private final boolean publicDeployment;

    public TestEmployeeSeeder(LocalUserRepository users, PasswordEncoder passwords,
            @Value("${authentication.test-employee-username}") String username,
            @Value("${authentication.test-employee-password}") String password,
            @Value("${authentication.test-employee-email}") String email,
            @Value("${authentication.public-deployment:false}") boolean publicDeployment) {
        this.users = users;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
        this.email = email;
        this.publicDeployment = publicDeployment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (publicDeployment && (password.length() < 12 || "Profesor123!".equals(password))) {
            throw new IllegalStateException("Za javni pristup postavi novu lozinku testnog zaposlenog.");
        }
        var existing = users.findByUsernameIgnoreCase(username);
        if (existing.isEmpty()) {
            users.save(new LocalUser(username, passwords.encode(password), email, "EMPLOYEE"));
        } else if (!passwords.matches(password, existing.get().getPasswordHash())) {
            var employee = existing.get();
            employee.updatePasswordHash(passwords.encode(password));
            users.save(employee);
        }
    }
}
