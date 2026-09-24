package com.attendance.course;

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

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "employee_email", nullable = false)
    private String employeeEmail;

    @Column(name = "employee_username", nullable = false, length = 100)
    private String employeeUsername;

    @Column(name = "employee_display_name", nullable = false, length = 200)
    private String employeeDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseEmployeeRole role;

    protected CourseEmployee() {}

    public CourseEmployee(Course course, EmployeeReference employee, CourseEmployeeRole role) {
        this.course = course;
        this.employeeId = employee.id();
        this.employeeEmail = employee.email();
        this.employeeUsername = employee.username();
        this.employeeDisplayName = employee.displayName();
        this.role = role;
    }

    public UUID getId() { return id; }
    public Course getCourse() { return course; }
    public UUID getEmployeeId() { return employeeId; }
    public String getEmployeeEmail() { return employeeEmail; }
    public String getEmployeeUsername() { return employeeUsername; }
    public String getEmployeeDisplayName() { return employeeDisplayName; }
    public CourseEmployeeRole getRole() { return role; }
}
