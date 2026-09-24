package com.attendance.analytics;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "authentication.jwt-secret=analytics-test-secret-at-least-32-characters")
@AutoConfigureMockMvc
class AnalyticsControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean CourseDataClient courses;

    @Test
    void acceptsOnlySignedEmployeeTokenAndForwardsItToCourseService() throws Exception {
        UUID offeringId = UUID.randomUUID();
        String path = "/api/analytics/offerings/" + offeringId + "/attendance-summary";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + token("STUDENT")))
                .andExpect(status().isForbidden());
        String employeeToken = token("EMPLOYEE");
        when(courses.load(offeringId, employeeToken)).thenReturn(
                new AnalyticsData.Source(List.of(), List.of(), List.of()));
        mvc.perform(get(path).header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedSessions").value(0))
                .andExpect(jsonPath("$.students.length()").value(0));
        verify(courses).load(offeringId, employeeToken);
    }

    private String token(String role) {
        byte[] bytes = "analytics-test-secret-at-least-32-characters".getBytes(StandardCharsets.UTF_8);
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        var jwk = new OctetSequenceKey.Builder(key).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("https://attendance-system.local")
                .audience(List.of("course-service")).subject(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("role", role).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
