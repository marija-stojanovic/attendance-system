package com.attendance.course;

import java.util.List;
import java.util.UUID;
import com.attendance.course.security.LocalUser;
import com.attendance.course.security.LocalUserRepository;
import com.attendance.course.sessions.TeachingSessionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseEmployeeRepository courseEmployees;
    private final LocalUserRepository localUsers;
    private final CourseOfferingRepository offerings;
    private final TeachingGroupRepository groups;
    private final EnrollmentRepository enrollments;
    private final TeachingSessionRepository sessions;

    public CourseService(CourseRepository courseRepository, CourseEmployeeRepository courseEmployees,
            LocalUserRepository localUsers, CourseOfferingRepository offerings, TeachingGroupRepository groups,
            EnrollmentRepository enrollments, TeachingSessionRepository sessions) {
        this.courseRepository = courseRepository;
        this.courseEmployees = courseEmployees;
        this.localUsers = localUsers;
        this.offerings = offerings;
        this.groups = groups;
        this.enrollments = enrollments;
        this.sessions = sessions;
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        if (courseRepository.existsByCode(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Predmet sa ovom šifrom već postoji.");
        }
        Course course = new Course(request.code(), request.name());
        return CourseResponse.from(courseRepository.saveAndFlush(course));
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request, UUID employeeId) {
        if (courseRepository.existsByCode(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Predmet sa ovom šifrom već postoji.");
        }
        LocalUser employee = findEmployee(employeeId);
        Course course = courseRepository.saveAndFlush(new Course(request.code(), request.name()));
        courseEmployees.save(new CourseEmployee(course, employee, CourseEmployeeRole.TEACHER));
        return CourseResponse.from(course);
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

    @Transactional
    public CourseResponse update(UUID id, CreateCourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Predmet nije pronađen."));
        if (courseRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Predmet sa ovom šifrom već postoji.");
        }
        course.update(request.code(), request.name());
        return CourseResponse.from(courseRepository.saveAndFlush(course));
    }

    @Transactional
    public void delete(UUID courseId, UUID employeeId) {
        Course course = requireCourseAccess(courseId, employeeId);
        if (!courseEmployees.existsByCourseIdAndEmployeeIdAndRole(courseId, employeeId,
                CourseEmployeeRole.TEACHER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Samo profesor povezan sa predmetom može obrisati predmet.");
        }
        List<Enrollment> courseEnrollments = enrollments
                .findByOfferingCourseIdOrderByStudentLastNameAscStudentFirstNameAscStudentIndexNumberAsc(courseId);
        enrollments.deleteAll(courseEnrollments);
        enrollments.flush();

        sessions.deleteAll(sessions.findByGroupOfferingCourseIdOrderByDateAscStartTimeAscIdAsc(courseId));
        sessions.flush();
        groups.deleteAll(groups.findByOfferingCourseId(courseId));
        groups.flush();
        offerings.deleteAll(offerings.findByCourseIdOrderByAcademicYearDescStudyProgramAscStudyTypeAsc(courseId));
        offerings.flush();
        courseEmployees.deleteAll(courseEmployees.findByCourseId(courseId));
        courseEmployees.flush();
        courseRepository.delete(course);
        courseRepository.flush();
    }

    public List<CourseResponse> findAll(UUID employeeId) {
        findEmployee(employeeId);
        return courseEmployees.findCoursesByEmployeeId(employeeId).stream().map(CourseResponse::from).toList();
    }

    public List<CourseEmployeeResponse> findEmployees(UUID courseId, UUID requestingEmployeeId) {
        requireCourseAccess(courseId, requestingEmployeeId);
        return courseEmployees.findByCourseIdOrderByEmployeeEmail(courseId).stream()
                .map(CourseEmployeeResponse::from).toList();
    }

    @Transactional
    public CourseEmployeeResponse assignEmployee(UUID courseId, UUID requestingEmployeeId,
            AssignCourseEmployeeRequest request) {
        Course course = requireCourseAccess(courseId, requestingEmployeeId);
        LocalUser employee = localUsers.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zaposleni nije pronađen."));
        if (courseEmployees.existsByCourseIdAndEmployeeId(courseId, employee.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Zaposleni je već povezan sa predmetom.");
        }
        return CourseEmployeeResponse.from(courseEmployees.saveAndFlush(
                new CourseEmployee(course, employee, request.role())));
    }

    private Course requireCourseAccess(UUID courseId, UUID employeeId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Predmet nije pronađen."));
        if (!courseEmployees.existsByCourseIdAndEmployeeId(courseId, employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nemate pristup ovom predmetu.");
        }
        return course;
    }

    private LocalUser findEmployee(UUID employeeId) {
        return localUsers.findById(employeeId)
                .filter(user -> "EMPLOYEE".equals(user.getRole()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Zaposleni nije pronađen."));
    }
}
