package com.attendance.course.imports;

import com.attendance.course.*;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportCommitService {
    private final CourseRepository courses;
    private final CourseOfferingRepository offerings;
    private final TeachingGroupRepository groups;
    private final StudentRepository students;
    private final EnrollmentRepository enrollments;
    private final StudentEmailGenerator emails;
    private final TransactionTemplate transactions;

    public ImportCommitService(CourseRepository courses, CourseOfferingRepository offerings,
            TeachingGroupRepository groups, StudentRepository students, EnrollmentRepository enrollments,
            StudentEmailGenerator emails, PlatformTransactionManager manager) {
        this.courses = courses;
        this.offerings = offerings;
        this.groups = groups;
        this.students = students;
        this.enrollments = enrollments;
        this.emails = emails;
        this.transactions = new TransactionTemplate(manager);
    }

    public ImportResult commit(ImportPreview preview, String academicYear) {
        if (!preview.valid() || preview.metadata() == null || preview.students().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Neispravan fajl nije moguće upisati.");
        }
        String year = academicYear == null ? "" : academicYear.strip();
        if (!year.matches("[0-9]{4}/[0-9]{4}")
                || Integer.parseInt(year.substring(5)) != Integer.parseInt(year.substring(0, 4)) + 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Akademska godina mora sadržati uzastopne godine, npr. 2026/2027.");
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return transactions.execute(transaction -> save(preview, year));
            } catch (DataIntegrityViolationException | ConcurrencyFailureException exception) {
                if (attempt == 4) {
                    throw conflict("Uvoz nije sačuvan zbog istovremenih izmjena ili sukoba podataka. Ponovite zahtjev.");
                }
            }
        }
        throw new IllegalStateException("Uvoz nije završen.");
    }

    private ImportResult save(ImportPreview preview, String year) {
        var metadata = preview.metadata();
        Course course = courses.findByCode(metadata.courseCode()).orElse(null);
        boolean courseCreated = course == null;
        if (courseCreated) {
            course = courses.saveAndFlush(new Course(metadata.courseCode(), metadata.courseName()));
        } else if (!same(course.getName(), metadata.courseName())) {
            throw conflict("Šifra predmeta postoji sa drugačijim nazivom.");
        }
        CourseOffering offering = offerings.findByCourseIdAndAcademicYearAndStudyProgramAndStudyType(
                course.getId(), year, metadata.studyProgram(), metadata.studyType()).orElse(null);
        boolean offeringCreated = offering == null;
        if (offeringCreated) {
            offering = offerings.saveAndFlush(new CourseOffering(course, new CreateOfferingRequest(year,
                    metadata.studyProgram(), metadata.studyType(), metadata.yearOfStudy(), metadata.semester())));
        } else if (offering.getYearOfStudy() != metadata.yearOfStudy() || offering.getSemester() != metadata.semester()) {
            throw conflict("Izvođenje predmeta postoji sa drugačijom godinom studija ili semestrom.");
        }
        Map<String, TeachingGroup> usedGroups = new LinkedHashMap<>();
        int createdGroups = 0;
        int createdStudents = 0;
        int createdEnrollments = 0;
        int memberships = 0;
        for (var row : preview.students().stream().sorted(Comparator.comparing(ImportPreview.StudentRow::indexNumber)).toList()) {
            TeachingGroup group = null;
            if (row.assignedGroup() != null) {
                group = usedGroups.get(row.assignedGroup());
                if (group == null) {
                    group = groups.findByOfferingIdAndActivityTypeAndGroupNumber(offering.getId(),
                            metadata.activityType(), row.assignedGroup()).orElse(null);
                    if (group == null) {
                        group = groups.saveAndFlush(new TeachingGroup(offering, metadata.activityType(), row.assignedGroup()));
                        createdGroups++;
                    }
                    usedGroups.put(row.assignedGroup(), group);
                }
            }
            Student student = students.findByIndexNumber(row.indexNumber()).orElse(null);
            if (student == null) {
                int suffix = 1;
                String email = emails.generate(row.firstName(), row.lastName(), suffix);
                while (students.existsByInstitutionalEmail(email)) {
                    email = emails.generate(row.firstName(), row.lastName(), ++suffix);
                }
                student = students.saveAndFlush(new Student(row.indexNumber(), row.firstName(), row.lastName(), email));
                createdStudents++;
            } else if (!same(student.getFirstName(), row.firstName()) || !same(student.getLastName(), row.lastName())) {
                throw conflict("Red " + row.row() + ": broj indeksa postoji sa drugačijim imenom ili prezimenom.");
            }
            Enrollment enrollment = enrollments.findByStudentIdAndOfferingId(student.getId(), offering.getId()).orElse(null);
            Set<TeachingGroup> selectedGroup = group == null ? Set.of() : Set.of(group);
            if (enrollment == null) {
                enrollments.saveAndFlush(new Enrollment(student, offering, row.status(), selectedGroup));
                createdEnrollments++;
                memberships += selectedGroup.size();
            } else {
                if (!same(enrollment.getStatus(), row.status())) {
                    throw conflict("Red " + row.row() + ": postojeći upis ima drugačiji status.");
                }
                var groupId = group == null ? null : group.getId();
                if (groupId != null && enrollment.getGroups().stream().noneMatch(existing -> existing.getId().equals(groupId))) {
                    enrollment.getGroups().add(group);
                    memberships++;
                }
            }
        }
        enrollments.flush();
        List<java.util.UUID> groupIds = usedGroups.values().stream().map(TeachingGroup::getId).toList();
        java.util.UUID groupId = groupIds.size() == 1 ? groupIds.getFirst() : null;
        return new ImportResult(course.getId(), offering.getId(), groupId, groupIds, courseCreated, offeringCreated,
                createdGroups > 0, createdGroups, createdStudents, preview.students().size() - createdStudents,
                createdEnrollments, preview.students().size() - createdEnrollments, memberships, preview.warnings());
    }

    private boolean same(String first, String second) {
        return Normalizer.normalize(ImportField.clean(first), Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .equals(Normalizer.normalize(ImportField.clean(second), Normalizer.Form.NFC).toLowerCase(Locale.ROOT));
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
