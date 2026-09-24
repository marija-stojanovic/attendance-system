package com.attendance.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtDecoder googleDecoder;
    private final JwtDecoder applicationDecoder;
    private final IdentityPolicy policy;
    private final StudentIdentityRepository students;
    private final LocalUserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    public AuthController(@Qualifier("googleJwtDecoder") JwtDecoder googleDecoder,
            @Qualifier("applicationJwtDecoder") JwtDecoder applicationDecoder,
            IdentityPolicy policy, StudentIdentityRepository students, LocalUserRepository users,
            PasswordEncoder passwords, TokenService tokens) {
        this.googleDecoder = googleDecoder;
        this.applicationDecoder = applicationDecoder;
        this.policy = policy;
        this.students = students;
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @PostMapping("/google")
    @Transactional
    public GoogleLoginResponse google(@Valid @RequestBody GoogleLogin request) {
        try {
            Jwt google = googleDecoder.decode(request.credential());
            policy.verifyGoogleStudent(google);
            String issuer = google.getIssuer().toString();
            String email = google.getClaimAsString("email");
            StudentIdentity student = students.findByOidcIssuerAndOidcSubject(issuer, google.getSubject())
                    .orElse(null);
            if (student == null) {
                student = students.findByInstitutionalEmailIgnoreCase(email).orElse(null);
                if (student != null && student.getOidcSubject() != null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "E-mail je već povezan sa drugim nalogom.");
                }
            }
            if (student == null) return GoogleLoginResponse.registration(tokens.issueRegistration(google));
            return GoogleLoginResponse.authenticated(tokens.issue(google.getSubject(), email, "STUDENT",
                    issuer, google.getSubject()));
        } catch (JwtException | org.springframework.security.core.AuthenticationException exception) {
            throw unauthorized();
        }
    }

    @PostMapping("/register")
    @Transactional
    public TokenService.LoginResponse register(@Valid @RequestBody StudentRegistration request) {
        try {
            Jwt registration = applicationDecoder.decode(request.registrationToken());
            if (!"student_registration".equals(registration.getClaimAsString("purpose"))) throw unauthorized();
            String issuer = registration.getClaimAsString("identity_issuer");
            String subject = registration.getClaimAsString("identity_subject");
            String email = registration.getClaimAsString("email");
            StudentIdentity student = students.findByOidcIssuerAndOidcSubject(issuer, subject).orElse(null);
            if (student == null) {
                student = students.findByInstitutionalEmailIgnoreCase(email).orElse(null);
                if (student != null && student.getOidcSubject() != null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "E-mail je već povezan sa drugim nalogom.");
                }
                if (student == null) {
                    if (students.existsByIndexNumber(request.indexNumber())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Student sa ovim brojem indeksa već postoji.");
                    }
                    student = new StudentIdentity(request.indexNumber(), request.firstName(), request.lastName(), email);
                }
                student.linkIdentity(issuer, subject);
                students.saveAndFlush(student);
            }
            return tokens.issue(subject, email, "STUDENT", issuer, subject);
        } catch (JwtException exception) {
            throw unauthorized();
        }
    }

    @PostMapping("/login")
    public TokenService.LoginResponse login(@Valid @RequestBody LocalLogin request) {
        LocalUser user = users.findByUsernameIgnoreCase(request.username())
                .filter(found -> passwords.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(this::unauthorized);
        return tokens.issue(user.getId().toString(), user.getEmail(), user.getRole(),
                "local", user.getId().toString());
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Prijava nije prihvaćena.");
    }

    public record GoogleLogin(@NotBlank String credential) {}
    public record LocalLogin(@NotBlank String username, @NotBlank String password) {}

    public record StudentRegistration(
            @NotBlank String registrationToken,
            @NotBlank(message = "Broj indeksa je obavezan.")
            @Size(max = 30, message = "Broj indeksa može imati najviše 30 znakova.")
            @Pattern(regexp = "[^\\s\\p{Z}]+", message = "Broj indeksa ne smije sadržati razmake.")
            String indexNumber,
            @NotBlank(message = "Ime je obavezno.")
            @Size(max = 100, message = "Ime može imati najviše 100 znakova.")
            @Pattern(regexp = "[\\p{L}\\p{M}]+(?:[ '\u2019-][\\p{L}\\p{M}]+)*",
                    message = "Ime smije sadržati slova, razmake, crtice i apostrofe.")
            String firstName,
            @NotBlank(message = "Prezime je obavezno.")
            @Size(max = 100, message = "Prezime može imati najviše 100 znakova.")
            @Pattern(regexp = "[\\p{L}\\p{M}]+(?:[ '\u2019-][\\p{L}\\p{M}]+)*",
                    message = "Prezime smije sadržati slova, razmake, crtice i apostrofe.")
            String lastName) {
        public StudentRegistration {
            indexNumber = normalize(indexNumber);
            firstName = normalize(firstName);
            lastName = normalize(lastName);
        }

        private static String normalize(String value) {
            return value == null ? null : value.replaceAll("[\\s\\p{Z}]+", " ").strip();
        }
    }

    public record GoogleLoginResponse(String token, Instant expiresAt, boolean registrationRequired,
                                      TokenService.RegistrationChallenge registration) {
        static GoogleLoginResponse authenticated(TokenService.LoginResponse login) {
            return new GoogleLoginResponse(login.token(), login.expiresAt(), false, null);
        }

        static GoogleLoginResponse registration(TokenService.RegistrationChallenge registration) {
            return new GoogleLoginResponse(null, null, true, registration);
        }
    }
}
