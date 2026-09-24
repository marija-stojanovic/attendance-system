package com.attendance.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentIdentityRepository extends JpaRepository<StudentIdentity, UUID> {
    Optional<StudentIdentity> findByOidcIssuerAndOidcSubject(String issuer, String subject);
    Optional<StudentIdentity> findByInstitutionalEmailIgnoreCase(String email);
    boolean existsByIndexNumber(String indexNumber);
}
