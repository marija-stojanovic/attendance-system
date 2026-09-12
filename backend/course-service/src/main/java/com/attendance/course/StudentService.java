package com.attendance.course;

import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentService {
    private final StudentRepository students;
    private final StudentEmailGenerator emails;
    private final TransactionTemplate transactions;

    public StudentService(StudentRepository students, StudentEmailGenerator emails, PlatformTransactionManager manager) {
        this.students = students;
        this.emails = emails;
        this.transactions = new TransactionTemplate(manager);
    }

    public StudentResponse create(CreateStudentRequest request) {
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                return transactions.execute(transaction -> {
                    if (students.existsByIndexNumber(request.indexNumber())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Student sa ovim brojem indeksa već postoji.");
                    }
                    int number = 1;
                    String email = emails.generate(request.firstName(), request.lastName(), number);
                    while (students.existsByInstitutionalEmail(email)) {
                        email = emails.generate(request.firstName(), request.lastName(), ++number);
                    }
                    return StudentResponse.from(students.saveAndFlush(new Student(request.indexNumber(),
                            request.firstName(), request.lastName(), email)));
                });
            } catch (DataIntegrityViolationException exception) {
                if (attempt == 9) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Student nije sačuvan zbog istovremenih izmjena. Ponovite zahtjev.");
                }
            }
        }
        throw new IllegalStateException("Student nije sačuvan.");
    }

    public List<StudentResponse> findAll() {
        return students.findAll(Sort.by("lastName", "firstName", "indexNumber")).stream()
                .map(StudentResponse::from).toList();
    }

    public StudentResponse findById(UUID id) {
        return StudentResponse.from(students.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Student nije pronađen.")));
    }
}
