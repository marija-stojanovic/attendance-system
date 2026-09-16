package com.attendance.course.security;

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

    public TestEmployeeSeeder(LocalUserRepository users, PasswordEncoder passwords,
            @Value("${authentication.test-employee-username}") String username,
            @Value("${authentication.test-employee-password}") String password,
            @Value("${authentication.test-employee-email}") String email) {
        this.users = users;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.findByUsernameIgnoreCase(username).isEmpty()) {
            users.save(new LocalUser(username, passwords.encode(password), email, "EMPLOYEE"));
        }
    }
}
