package com.attendance.course;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService students;

    public StudentController(StudentService students) {
        this.students = students;
    }

    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request) {
        StudentResponse student = students.create(request);
        return ResponseEntity.created(URI.create("/api/students/" + student.id())).body(student);
    }

    @GetMapping
    public List<StudentResponse> findAll() {
        return students.findAll();
    }

    @GetMapping("/{id}")
    public StudentResponse findById(@PathVariable UUID id) {
        return students.findById(id);
    }
}
