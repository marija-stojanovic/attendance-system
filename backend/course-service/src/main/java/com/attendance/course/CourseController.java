package com.attendance.course;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        CourseResponse course = jwt == null ? courseService.create(request)
                : courseService.create(request, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/courses/" + course.id())).body(course);
    }

    @GetMapping
    public List<CourseResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        return jwt == null ? courseService.findAll() : courseService.findAll(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    public CourseResponse findById(@PathVariable UUID id) {
        return courseService.findById(id);
    }

    @PutMapping("/{id}")
    public CourseResponse update(@PathVariable UUID id, @Valid @RequestBody CreateCourseRequest request) {
        return courseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        courseService.delete(id, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/employees")
    public List<CourseEmployeeResponse> findEmployees(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return courseService.findEmployees(id, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{id}/employees")
    public ResponseEntity<CourseEmployeeResponse> assignEmployee(@PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssignCourseEmployeeRequest request) {
        CourseEmployeeResponse employee = courseService.assignEmployee(
                id, UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/courses/" + id + "/employees/" + employee.id())).body(employee);
    }
}
