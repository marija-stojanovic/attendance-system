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
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
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

    public SessionQrService(TeachingSessionRepository sessions, Clock qrClock, SecretKey applicationKey,
            @Value("${session-qr.rotation-seconds:30}") int interval) throws java.security.GeneralSecurityException {
        if (interval < 5 || interval > 300) {
            throw new IllegalArgumentException("Interval QR koda mora biti između 5 i 300 sekundi.");
        }
        this.sessions = sessions;
        this.clock = qrClock;
        this.interval = interval;
        Mac derivation = Mac.getInstance("HmacSHA256");
        derivation.init(applicationKey);
        signingKey = derivation.doFinal(AUDIENCE.getBytes(StandardCharsets.UTF_8));
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
            return new QrResponse(session.getId(), token, image(token), expiresAt, now);
        } catch (JOSEException exception) {
            throw new IllegalStateException("Generisanje QR tokena nije uspjelo.", exception);
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
            throw new IllegalStateException("Generisanje QR slike nije uspjelo.", exception);
        }
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR token nije važeći.");
    }

    public record QrResponse(UUID sessionId, String token, String imageDataUrl, Instant expiresAt, Instant serverTime) {}
}
