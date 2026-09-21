package com.attendance.course.sessions;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class SessionQrService {
    private static final String AUDIENCE = "attendance-qr";
    private static final JOSEObjectType TYPE = new JOSEObjectType("attendance-qr+jwt");
    private final TeachingSessionRepository sessions;
    private final Clock clock;
    private final int interval;
    private final byte[] signingKey;
    private final String frontendOrigin;

    @Autowired
    public SessionQrService(TeachingSessionRepository sessions, Clock qrClock, SecretKey applicationKey,
            @Value("${session-qr.rotation-seconds:30}") int interval,
            @Value("${authentication.frontend-origin:http://localhost:5173}") String frontendOrigin)
            throws java.security.GeneralSecurityException {
        if (interval < 5 || interval > 300) {
            throw new IllegalArgumentException("Interval QR koda mora biti između 5 i 300 sekundi.");
        }
        this.sessions = sessions;
        this.clock = qrClock;
        this.interval = interval;
        this.frontendOrigin = frontendOrigin.replaceAll("/+$", "");
        Mac derivation = Mac.getInstance("HmacSHA256");
        derivation.init(applicationKey);
        signingKey = derivation.doFinal(AUDIENCE.getBytes(StandardCharsets.UTF_8));
    }

    SessionQrService(TeachingSessionRepository sessions, Clock qrClock, SecretKey applicationKey, int interval)
            throws java.security.GeneralSecurityException {
        this(sessions, qrClock, applicationKey, interval, "http://localhost:5173");
    }

    public Instant now() {
        return clock.instant();
    }

    public QrResponse current(UUID sessionId) {
        TeachingSession session = sessions.findById(sessionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesija nije pronađena."));
        return generate(session);
    }

    public QrResponse generate(TeachingSession session) {
        Instant now = now();
        Instant issuedAt = windowStart(session, now);
        Instant expiresAt = issuedAt.plusSeconds(interval);
        try {
            var claims = new JWTClaimsSet.Builder().subject(session.getId().toString())
                    .audience(AUDIENCE).issueTime(Date.from(issuedAt)).expirationTime(Date.from(expiresAt)).build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(TYPE).build(), claims);
            jwt.sign(new MACSigner(signingKey));
            String token = jwt.serialize();
            String scanUrl = frontendOrigin + "/?attendanceToken="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8);
            return new QrResponse(session.getId(), token, pin(session, issuedAt), image(scanUrl), scanUrl, expiresAt, now);
        } catch (JOSEException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "QR kod trenutno nije dostupan.", exception);
        }
    }

    public UUID validate(UUID sessionId, String token) {
        try {
            if (token == null || token.length() > 2048) throw invalid();
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !TYPE.equals(jwt.getHeader().getType()) || !jwt.verify(new MACVerifier(signingKey))) throw invalid();
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!sessionId.toString().equals(claims.getSubject())
                    || !List.of(AUDIENCE).equals(claims.getAudience())) throw invalid();
            TeachingSession session = sessions.findById(sessionId).orElseThrow(() -> invalid());
            Instant now = now();
            Instant issuedAt = windowStart(session, now);
            if (!Date.from(issuedAt).equals(claims.getIssueTime())
                    || !Date.from(issuedAt.plusSeconds(interval)).equals(claims.getExpirationTime())) throw invalid();
            return sessionId;
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    public UUID validate(String token) {
        try {
            if (token == null || token.length() > 2048) throw invalid();
            return validate(UUID.fromString(SignedJWT.parse(token).getJWTClaimsSet().getSubject()), token);
        } catch (ParseException | IllegalArgumentException | NullPointerException exception) {
            throw invalid();
        }
    }

    public UUID validatePin(String value) {
        if (value == null || !value.matches("\\d{6}")) throw invalidPin();
        byte[] supplied = value.getBytes(StandardCharsets.US_ASCII);
        List<UUID> matches = sessions.findByStatus(SessionStatus.ACTIVE).stream()
                .filter(session -> MessageDigest.isEqual(
                        pin(session, windowStart(session, now())).getBytes(StandardCharsets.US_ASCII), supplied))
                .map(TeachingSession::getId).toList();
        if (matches.size() != 1) throw invalidPin();
        return matches.getFirst();
    }

    private Instant windowStart(TeachingSession session, Instant now) {
        if (session.getStatus() != SessionStatus.ACTIVE || session.getQrStartedAt() == null
                || now.isBefore(session.getQrStartedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "QR kod je dostupan samo za aktivnu sesiju.");
        }
        long elapsed = now.getEpochSecond() - session.getQrStartedAt().getEpochSecond();
        return session.getQrStartedAt().plusSeconds(elapsed / interval * interval);
    }

    private String image(String token) {
        try {
            var matrix = new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 420, 420);
            var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xffffff);
                }
            }
            var output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (java.io.IOException | com.google.zxing.WriterException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "QR kod trenutno nije dostupan.", exception);
        }
    }

    private String pin(TeachingSession session, Instant issuedAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(("pin:" + session.getId() + ":" + issuedAt.getEpochSecond())
                    .getBytes(StandardCharsets.UTF_8));
            long number = ((digest[0] & 0xffL) << 24) | ((digest[1] & 0xffL) << 16)
                    | ((digest[2] & 0xffL) << 8) | (digest[3] & 0xffL);
            return "%06d".formatted(number % 1_000_000);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Generisanje PIN koda nije uspjelo.", exception);
        }
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR token nije važeći.");
    }

    private ResponseStatusException invalidPin() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "PIN kod nije važeći.");
    }

    public record QrResponse(UUID sessionId, String token, String pin, String imageDataUrl, String scanUrl,
            Instant expiresAt, Instant serverTime) {}
}
