package com.attendance.course.sessions;

import com.attendance.course.*;
import com.attendance.course.security.TokenService;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.nimbusds.jwt.SignedJWT;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${QR_TEST_DB_URL:jdbc:h2:mem:qr_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "session-qr.rotation-seconds=10" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "EMPLOYEE")
class SessionQrTests {
    @Autowired private SessionQrService qr;
    @Autowired private TeachingSessionService service;
    @Autowired private TeachingSessionRepository sessions;
    @Autowired private CourseRepository courses;
    @Autowired private CourseOfferingRepository offerings;
    @Autowired private TeachingGroupRepository groups;
    @Autowired private TokenService loginTokens;
    @Autowired private SecretKey applicationKey;
    @Autowired private MockMvc mvc;
    @MockitoBean private Clock qrClock;
    private final Instant start = Instant.parse("2026-10-01T09:00:00Z");
    private UUID id;

    @BeforeEach
    void setUp() {
        when(qrClock.instant()).thenReturn(start);
        sessions.deleteAll();
        groups.deleteAll();
        offerings.deleteAll();
        courses.deleteAll();
        Course course = courses.saveAndFlush(new Course("QR-1", "Testni predmet"));
        CourseOffering offering = offerings.saveAndFlush(new CourseOffering(course,
                new CreateOfferingRequest("2026/2027", "Računarstvo", "OSNOVNE", (short) 1, (short) 1)));
        TeachingGroup group = groups.saveAndFlush(new TeachingGroup(offering, ActivityType.LECTURE, "1"));
        id = sessions.saveAndFlush(new TeachingSession(group, LocalDate.of(2026, 10, 1), LocalTime.NOON)).getId();
    }

    @Test
    void generatesDecodableImageWithMinimalSignedTokenOnActivation() throws Exception {
        service.activate(id);
        assertThat(sessions.findById(id).orElseThrow().getQrStartedAt()).isEqualTo(start);
        var response = qr.current(id);
        var claims = SignedJWT.parse(response.token()).getJWTClaimsSet();
        assertThat(claims.getClaims()).containsOnlyKeys("sub", "aud", "iat", "exp");
        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(response.expiresAt()).isEqualTo(start.plusSeconds(10));
        assertThat(qr.validate(id, response.token())).isEqualTo(id);
        var image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder()
                .decode(response.imageDataUrl().substring("data:image/png;base64,".length()))));
        int width = image.getWidth(), height = image.getHeight();
        var source = new RGBLuminanceSource(width, height, image.getRGB(0, 0, width, height, null, 0, width));
        assertThat(new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText())
                .isEqualTo(response.token());
        mvc.perform(get("/api/sessions/{id}/qr", id)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("sessionId").value(id.toString()))
                .andExpect(jsonPath("token").value(response.token()));
    }

    @Test
    void rotatesAtBoundaryAndRejectsExpiredTokenWithoutAnotherRead() {
        service.activate(id);
        String original = qr.current(id).token();
        when(qrClock.instant()).thenReturn(start.plusSeconds(9).plusMillis(999));
        assertThat(qr.current(id).token()).isEqualTo(original);
        assertThat(qr.validate(id, original)).isEqualTo(id);
        when(qrClock.instant()).thenReturn(start.plusSeconds(10));
        assertThatThrownBy(() -> qr.validate(id, original)).isInstanceOf(ResponseStatusException.class);
        var next = qr.current(id);
        assertThat(next.token()).isNotEqualTo(original);
        assertThat(next.expiresAt()).isEqualTo(start.plusSeconds(20));
        assertThat(qr.validate(id, next.token())).isEqualTo(id);
        when(qrClock.instant()).thenReturn(start.plusSeconds(65));
        assertThat(qr.current(id).expiresAt()).isEqualTo(start.plusSeconds(70));
    }

    @Test
    void rejectsCreatedAndClosedSessions() throws Exception {
        mvc.perform(get("/api/sessions/{id}/qr", id)).andExpect(status().isConflict());
        service.activate(id);
        String token = qr.current(id).token();
        service.close(id);
        assertThatThrownBy(() -> qr.validate(id, token)).isInstanceOf(ResponseStatusException.class);
        mvc.perform(get("/api/sessions/{id}/qr", id)).andExpect(status().isConflict());
        mvc.perform(get("/api/sessions/{id}/qr", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsDifferentSessionAndModifiedSignature() {
        service.activate(id);
        String token = qr.current(id).token();
        assertThatThrownBy(() -> qr.validate(UUID.randomUUID(), token)).isInstanceOf(ResponseStatusException.class);
        int signature = token.lastIndexOf('.') + 1;
        String modified = token.substring(0, signature) + (token.charAt(signature) == 'A' ? 'B' : 'A')
                + token.substring(signature + 1);
        assertThatThrownBy(() -> qr.validate(id, modified)).isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-token", "eyJhbGciOiJub25lIn0.e30.", "e30.e30.e30"})
    void rejectsMalformedTokens(String token) {
        service.activate(id);
        assertThatThrownBy(() -> qr.validate(id, token)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void separatesQrTokensFromLoginTokens() throws Exception {
        service.activate(id);
        var login = loginTokens.issue(id.toString(), "test@example.org", "EMPLOYEE", "local", "test");
        assertThatThrownBy(() -> qr.validate(id, login.token())).isInstanceOf(ResponseStatusException.class);
        mvc.perform(get("/api/sessions/{id}/qr", id)
                .header("Authorization", "Bearer " + qr.current(id).token())).andExpect(status().isUnauthorized());
    }

    @Test
    void preservesTokensAcrossServiceRestart() throws Exception {
        service.activate(id);
        String token = qr.current(id).token();
        var restarted = new SessionQrService(sessions, qrClock, applicationKey, 10);
        assertThat(restarted.current(id).token()).isEqualTo(token);
        assertThat(restarted.validate(id, token)).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 301})
    void rejectsInvalidIntervals(int interval) {
        assertThatThrownBy(() -> new SessionQrService(sessions, qrClock, applicationKey, interval))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void deniesStudentsAccessToQrDisplay() throws Exception {
        mvc.perform(get("/api/sessions/{id}/qr", id)).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/sessions/{id}/qr", id)).andExpect(status().isUnauthorized());
    }
}
