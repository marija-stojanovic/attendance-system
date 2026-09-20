package com.attendance.course.attendance;

import com.attendance.course.Student;
import com.attendance.course.sessions.TeachingSession;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendances", uniqueConstraints = @UniqueConstraint(name = "uq_attendance_session_student",
        columnNames = {"teaching_session_id", "student_id"}))
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teaching_session_id", nullable = false)
    private TeachingSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected Attendance() {
    }

    public Attendance(TeachingSession session, Student student, Instant recordedAt) {
        this.session = session;
        this.student = student;
        this.recordedAt = recordedAt;
    }

    public UUID getId() { return id; }
    public TeachingSession getSession() { return session; }
    public Student getStudent() { return student; }
    public Instant getRecordedAt() { return recordedAt; }
}
