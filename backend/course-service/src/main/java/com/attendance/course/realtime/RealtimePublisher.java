package com.attendance.course.realtime;

import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class RealtimePublisher {
    private final ApplicationEventPublisher events;

    public RealtimePublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void sessionChanged(UUID courseId, UUID offeringId, UUID sessionId) {
        afterCommit(new RealtimeEvent("SESSION_CHANGED", courseId, offeringId, sessionId));
    }

    public void attendanceChanged(UUID courseId, UUID offeringId, UUID sessionId) {
        afterCommit(new RealtimeEvent("ATTENDANCE_CHANGED", courseId, offeringId, sessionId));
    }

    public void qrChanged(UUID courseId, UUID offeringId, UUID sessionId) {
        events.publishEvent(new RealtimeEvent("QR_CHANGED", courseId, offeringId, sessionId));
    }

    private void afterCommit(RealtimeEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            events.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                events.publishEvent(event);
            }
        });
    }
}
