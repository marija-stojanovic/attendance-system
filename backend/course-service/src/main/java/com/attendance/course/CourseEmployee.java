package com.attendance.course;

import com.attendance.course.security.LocalUser;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "course_employees", uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "employee_id"}))
public class CourseEmployee {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private LocalUser employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseEmployeeRole role;

    protected CourseEmployee() {}

    public CourseEmployee(Course course, LocalUser employee, CourseEmployeeRole role) {
        this.course = course;
        this.employee = employee;
        this.role = role;
    }

    public UUID getId() { return id; }
    public Course getCourse() { return course; }
    public LocalUser getEmployee() { return employee; }
    public CourseEmployeeRole getRole() { return role; }
}
