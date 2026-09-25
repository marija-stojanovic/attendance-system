package com.attendance.course.sessions;

import java.time.Clock;
import java.time.Instant;
import com.attendance.course.realtime.QrRotationNotifier;
import com.attendance.course.realtime.RealtimePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeachingSessionExpirationService {
    private final TeachingSessionRepository sessions;
    private final Clock clock;
    private final long maximumActiveSeconds;
    private final RealtimePublisher realtime;
    private final QrRotationNotifier qrRotations;

    public TeachingSessionExpirationService(TeachingSessionRepository sessions, Clock qrClock,
            @Value("${teaching-session.max-active-seconds:14400}") long maximumActiveSeconds,
            RealtimePublisher realtime, QrRotationNotifier qrRotations) {
        if (maximumActiveSeconds < 300 || maximumActiveSeconds > 86400) {
            throw new IllegalArgumentException("Trajanje aktivne sesije mora biti između 5 minuta i 24 sata.");
        }
        this.sessions = sessions;
        this.clock = qrClock;
        this.maximumActiveSeconds = maximumActiveSeconds;
        this.realtime = realtime;
        this.qrRotations = qrRotations;
    }

    @Scheduled(fixedDelayString = "${teaching-session.expiration-check-milliseconds:60000}",
            initialDelayString = "${teaching-session.expiration-check-milliseconds:60000}")
    @Transactional
    public int closeExpired() {
        Instant cutoff = clock.instant().minusSeconds(maximumActiveSeconds);
        var expired = sessions.findByStatusAndQrStartedAtLessThanEqual(SessionStatus.ACTIVE, cutoff);
        expired.forEach(session -> {
            session.close();
            qrRotations.cancel(session.getId());
            realtime.sessionChanged(session.getCourse().getId(), session.getGroup().getOffering().getId(),
                    session.getId());
        });
        return expired.size();
    }
}
