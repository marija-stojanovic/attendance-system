package com.attendance.course.realtime;

import com.attendance.course.sessions.SessionStatus;
import com.attendance.course.sessions.TeachingSession;
import com.attendance.course.sessions.TeachingSessionRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QrRotationNotifier {
    private final TeachingSessionRepository sessions;
    private final RealtimePublisher realtime;
    private final Clock clock;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public QrRotationNotifier(TeachingSessionRepository sessions, RealtimePublisher realtime, Clock qrClock) {
        this.sessions = sessions;
        this.realtime = realtime;
        this.clock = qrClock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void restore() {
        sessions.findByStatus(SessionStatus.ACTIVE).forEach(this::schedule);
    }

    public void schedule(TeachingSession session) {
        cancel(session.getId());
        long interval = session.getQrRotationSeconds();
        long elapsed = Duration.between(session.getQrStartedAt(), clock.instant()).getSeconds();
        long initialDelay = interval - Math.floorMod(elapsed, interval);
        UUID courseId = session.getCourse().getId();
        UUID offeringId = session.getGroup().getOffering().getId();
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> realtime.qrChanged(courseId, offeringId, session.getId()),
                initialDelay, interval, TimeUnit.SECONDS);
        tasks.put(session.getId(), task);
    }

    public void cancel(UUID sessionId) {
        ScheduledFuture<?> task = tasks.remove(sessionId);
        if (task != null) task.cancel(false);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }
}
