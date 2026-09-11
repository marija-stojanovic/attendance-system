package com.attendance.course;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        if (courseRepository.existsByCode(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Predmet sa ovom šifrom već postoji.");
        }
        Course course = new Course(request.code(), request.name());
        return CourseResponse.from(courseRepository.saveAndFlush(course));
    }

    public List<CourseResponse> findAll() {
        return courseRepository.findAll(Sort.by("code")).stream()
                .map(CourseResponse::from)
                .toList();
    }

    public CourseResponse findById(UUID id) {
        return courseRepository.findById(id)
                .map(CourseResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Predmet nije pronađen."));
    }
}
