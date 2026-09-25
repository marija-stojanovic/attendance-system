package com.attendance.course.sessions;

import com.attendance.course.ActivityType;
import com.attendance.course.Course;
import com.attendance.course.TeachingGroup;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "teaching_sessions")
public class TeachingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teaching_group_id", nullable = false)
    private TeachingGroup group;

    @NotNull
    @Column(name = "session_date", nullable = false)
    private LocalDate date;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.CREATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "qr_started_at")
    private Instant qrStartedAt;

    @Column(name = "qr_rotation_seconds", nullable = false)
    private int qrRotationSeconds = 30;

    protected TeachingSession() {
    }

    public TeachingSession(TeachingGroup group, LocalDate date, LocalTime startTime) {
        this(group, date, startTime, 30);
    }

    public TeachingSession(TeachingGroup group, LocalDate date, LocalTime startTime, int qrRotationSeconds) {
        this.group = group;
        this.date = date;
        this.startTime = startTime;
        this.qrRotationSeconds = qrRotationSeconds;
    }

    @PrePersist
    void beforeInsert() {
        createdAt = Instant.now();
    }

    void update(TeachingGroup group, LocalDate date, LocalTime startTime) {
        update(group, date, startTime, qrRotationSeconds);
    }

    void update(TeachingGroup group, LocalDate date, LocalTime startTime, int qrRotationSeconds) {
        this.group = group;
        this.date = date;
        this.startTime = startTime;
        this.qrRotationSeconds = qrRotationSeconds;
    }

    void activate(Instant now) {
        status = SessionStatus.ACTIVE;
        qrStartedAt = now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    void close() {
        status = SessionStatus.CLOSED;
    }

    public UUID getId() {
        return id;
    }

    public TeachingGroup getGroup() {
        return group;
    }

    public Course getCourse() {
        return group.getOffering().getCourse();
    }

    public ActivityType getActivityType() {
        return group.getActivityType();
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getQrStartedAt() {
        return qrStartedAt;
    }

    public int getQrRotationSeconds() {
        return qrRotationSeconds;
    }
}
