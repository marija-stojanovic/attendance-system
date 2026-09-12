package com.attendance.course;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, UUID> {
    boolean existsByIndexNumber(String indexNumber);
    boolean existsByInstitutionalEmail(String institutionalEmail);
}
