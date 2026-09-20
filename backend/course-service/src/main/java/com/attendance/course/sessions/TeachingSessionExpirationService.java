package com.attendance.course.sessions;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeachingSessionExpirationService {
    private final TeachingSessionRepository sessions;
    private final Clock clock;
    private final long maximumActiveSeconds;

    public TeachingSessionExpirationService(TeachingSessionRepository sessions, Clock qrClock,
            @Value("${teaching-session.max-active-seconds:14400}") long maximumActiveSeconds) {
        if (maximumActiveSeconds < 300 || maximumActiveSeconds > 86400) {
            throw new IllegalArgumentException("Trajanje aktivne sesije mora biti između 5 minuta i 24 sata.");
        }
        this.sessions = sessions;
        this.clock = qrClock;
        this.maximumActiveSeconds = maximumActiveSeconds;
    }

    @Scheduled(fixedDelayString = "${teaching-session.expiration-check-milliseconds:60000}",
            initialDelayString = "${teaching-session.expiration-check-milliseconds:60000}")
    @Transactional
    public int closeExpired() {
        Instant cutoff = clock.instant().minusSeconds(maximumActiveSeconds);
        var expired = sessions.findByStatusAndQrStartedAtLessThanEqual(SessionStatus.ACTIVE, cutoff);
        expired.forEach(TeachingSession::close);
        return expired.size();
    }
}
