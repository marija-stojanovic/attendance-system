package com.attendance.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "students")
public class StudentIdentity {
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudentIdentity() {}

    public StudentIdentity(String indexNumber, String firstName, String lastName, String institutionalEmail) {
        this.indexNumber = indexNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.institutionalEmail = institutionalEmail;
    }

    @PrePersist
    void beforeInsert() { createdAt = Instant.now(); }

    public void linkIdentity(String issuer, String subject) {
        oidcIssuer = issuer;
        oidcSubject = subject;
    }

    public UUID getId() { return id; }
    public String getOidcSubject() { return oidcSubject; }
}
