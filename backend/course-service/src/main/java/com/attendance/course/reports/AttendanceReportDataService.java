package com.attendance.course.reports;

import com.attendance.course.CourseEmployeeRepository;
import com.attendance.course.CourseOffering;
import com.attendance.course.CourseOfferingRepository;
import com.attendance.course.attendance.AttendanceRepository;
import com.attendance.course.sessions.TeachingSessionRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AttendanceReportDataService {
    private final CourseOfferingRepository offerings;
    private final TeachingSessionRepository sessions;
    private final AttendanceRepository attendances;
    private final CourseEmployeeRepository courseEmployees;

    public AttendanceReportDataService(CourseOfferingRepository offerings, TeachingSessionRepository sessions,
            AttendanceRepository attendances, CourseEmployeeRepository courseEmployees) {
        this.offerings = offerings;
        this.sessions = sessions;
        this.attendances = attendances;
        this.courseEmployees = courseEmployees;
    }

    public ReportData.Offering offering(UUID offeringId, UUID employeeId) {
        CourseOffering offering = offerings.findById(offeringId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Izvođenje predmeta nije pronađeno."));
        return offeringData(offering, employeeId);
    }

    public ReportData.Session session(UUID sessionId, UUID employeeId) {
        var session = sessions.findById(sessionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesija nije pronađena."));
        var offering = offeringData(session.getGroup().getOffering(), employeeId);
        var students = attendances.findBySessionIdOrderByRecordedAtAscStudentLastNameAscStudentFirstNameAsc(sessionId)
                .stream().map(attendance -> {
                    var student = attendance.getStudent();
                    return new ReportData.Student(student.getId(), student.getFirstName(), student.getLastName(),
                            student.getIndexNumber(), student.getInstitutionalEmail());
                }).toList();
        return new ReportData.Session(sessionId, offering, session.getDate(), session.getStartTime(),
                session.getStatus(), session.getActivityType(), session.getGroup().getGroupNumber(), students);
    }

    private ReportData.Offering offeringData(CourseOffering offering, UUID employeeId) {
        var course = offering.getCourse();
        var assigned = courseEmployees.findByCourseIdOrderByEmployeeEmail(course.getId());
        if (assigned.stream().noneMatch(item -> item.getEmployeeId().equals(employeeId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nemate pristup izvještaju ovog predmeta.");
        }
        var employees = assigned.stream().map(item -> new ReportData.Employee(
                item.getEmployeeDisplayName(), item.getEmployeeEmail(), item.getRole())).toList();
        return new ReportData.Offering(offering.getId(), course.getCode(), course.getName(),
                offering.getAcademicYear(), offering.getStudyProgram(), offering.getStudyType(),
                offering.getYearOfStudy(), offering.getSemester(), employees);
    }
}
