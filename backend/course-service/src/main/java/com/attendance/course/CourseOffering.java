package com.attendance.course;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_offerings", uniqueConstraints = @UniqueConstraint(name = "uq_course_offering",
        columnNames = {"course_id", "academic_year", "study_program", "study_type"}))
public class CourseOffering {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "academic_year", nullable = false, length = 9)
    private String academicYear;

    @Column(name = "study_program", nullable = false, columnDefinition = "text")
    private String studyProgram;

    @Column(name = "study_type", nullable = false, length = 100)
    private String studyType;

    @Column(name = "year_of_study", nullable = false)
    private short yearOfStudy;

    @Column(nullable = false)
    private short semester;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CourseOffering() {
    }

    public CourseOffering(Course course, CreateOfferingRequest request) {
        this.course = course;
        academicYear = request.academicYear();
        studyProgram = request.studyProgram();
        studyType = request.studyType();
        yearOfStudy = request.yearOfStudy();
        semester = request.semester();
    }

    @PrePersist
    void beforeInsert() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public Course getCourse() { return course; }
    public String getAcademicYear() { return academicYear; }
    public String getStudyProgram() { return studyProgram; }
    public String getStudyType() { return studyType; }
    public short getYearOfStudy() { return yearOfStudy; }
    public short getSemester() { return semester; }
}
