package com.attendance.course;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "students", uniqueConstraints = @UniqueConstraint(
        name = "uq_student_identity", columnNames = {"oidc_issuer", "oidc_subject"}))
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "index_number", nullable = false, unique = true, length = 30)
    private String indexNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "institutional_email", unique = true, length = 255)
    private String institutionalEmail;

    @Column(name = "oidc_issuer")
    private String oidcIssuer;

    @Column(name = "oidc_subject")
    private String oidcSubject;

    public String getOidcSubject() { return oidcSubject; }
    public void linkIdentity(String issuer, String subject) {
        this.oidcIssuer = issuer;
        this.oidcSubject = subject;
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Student() {
    }

    public Student(String indexNumber, String firstName, String lastName, String institutionalEmail) {
        this.indexNumber = indexNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.institutionalEmail = institutionalEmail;
    }

    @PrePersist
    void beforeInsert() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getIndexNumber() { return indexNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getInstitutionalEmail() { return institutionalEmail; }
    public Instant getCreatedAt() { return createdAt; }
}
