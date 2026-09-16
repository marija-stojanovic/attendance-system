package com.attendance.course.security;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.assertThat;

class TokenValidationTests {
    @Test void verifiesSignatureAndRejectsForeignKey() throws Exception {
        var key = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
        var other = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
        var decoder = org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(SecurityConfig.tokenValidator("https://issuer.example", "course-service"));
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("https://issuer.example")
                .audience("course-service").subject("student")
                .expirationTime(java.util.Date.from(Instant.now().plusSeconds(120))).build();
        var valid = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256), claims);
        valid.sign(new com.nimbusds.jose.crypto.RSASSASigner(key));
        assertThat(decoder.decode(valid.serialize()).getSubject()).isEqualTo("student");
        var forged = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256), claims);
        forged.sign(new com.nimbusds.jose.crypto.RSASSASigner(other));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> decoder.decode(forged.serialize()))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
    @Test void checksIssuerAudienceExpirationAndSubject() {
        var validator = SecurityConfig.tokenValidator("https://issuer.example", "course-service");
        assertThat(validator.validate(token("https://issuer.example", "course-service", "user", 120)).hasErrors()).isFalse();
        assertThat(validator.validate(token("https://other.example", "course-service", "user", 120)).hasErrors()).isTrue();
        assertThat(validator.validate(token("https://issuer.example", "other-api", "user", 120)).hasErrors()).isTrue();
        assertThat(validator.validate(token("https://issuer.example", "course-service", "user", -120)).hasErrors()).isTrue();
        assertThat(validator.validate(token("https://issuer.example", "course-service", "", 120)).hasErrors()).isTrue();
    }
    private Jwt token(String issuer, String audience, String subject, int expiresIn) {
        return Jwt.withTokenValue("value").header("alg", "RS256").issuer(issuer)
                .audience(List.of(audience)).subject(subject).issuedAt(Instant.now().minusSeconds(300))
                .expiresAt(Instant.now().plusSeconds(expiresIn)).build();
    }
}
