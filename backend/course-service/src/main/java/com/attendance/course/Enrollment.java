package com.attendance.course;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "enrollments", uniqueConstraints = @UniqueConstraint(name = "uq_enrollment",
        columnNames = {"student_id", "course_offering_id"}))
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false)
    private CourseOffering offering;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @ManyToMany
    @JoinTable(name = "enrollment_groups", joinColumns = @JoinColumn(name = "enrollment_id"),
            inverseJoinColumns = @JoinColumn(name = "teaching_group_id"))
    private Set<TeachingGroup> groups = new LinkedHashSet<>();

    protected Enrollment() {
    }

    public Enrollment(Student student, CourseOffering offering, String status, Set<TeachingGroup> groups) {
        this.student = student;
        this.offering = offering;
        this.status = status;
        this.groups.addAll(groups);
    }

    @PrePersist
    void beforeInsert() { enrolledAt = Instant.now(); }

    public UUID getId() { return id; }
    public Student getStudent() { return student; }
    public CourseOffering getOffering() { return offering; }
    public String getStatus() { return status; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Set<TeachingGroup> getGroups() { return groups; }
    public void setStatus(String status) { this.status = status; }

    public void replaceGroups(Set<TeachingGroup> groups) {
        this.groups.clear();
        this.groups.addAll(groups);
    }
}
