package com.attendance.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final long lifetimeSeconds;

    public TokenService(JwtEncoder encoder,
            @Value("${authentication.app-issuer}") String issuer,
            @Value("${authentication.audience}") String audience,
            @Value("${authentication.token-lifetime-seconds}") long lifetimeSeconds) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.lifetimeSeconds = lifetimeSeconds;
    }

    public LoginResponse issue(String subject, String email, String role,
            String identityIssuer, String identitySubject) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(lifetimeSeconds);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(subject).audience(List.of(audience))
                .issuedAt(now).expiresAt(expiresAt)
                .claim("email", email).claim("role", role)
                .claim("identity_issuer", identityIssuer)
                .claim("identity_subject", identitySubject)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, expiresAt);
    }

    public RegistrationChallenge issueRegistration(Jwt google) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(google.getSubject()).audience(List.of(audience))
                .issuedAt(now).expiresAt(expiresAt)
                .claim("purpose", "student_registration")
                .claim("email", google.getClaimAsString("email"))
                .claim("first_name", google.getClaimAsString("given_name"))
                .claim("last_name", google.getClaimAsString("family_name"))
                .claim("identity_issuer", google.getIssuer().toString())
                .claim("identity_subject", google.getSubject())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new RegistrationChallenge(token, google.getClaimAsString("email"),
                google.getClaimAsString("given_name"), google.getClaimAsString("family_name"), expiresAt);
    }

    public record LoginResponse(String token, Instant expiresAt) {}
    public record RegistrationChallenge(String token, String email, String firstName,
                                        String lastName, Instant expiresAt) {}
}
