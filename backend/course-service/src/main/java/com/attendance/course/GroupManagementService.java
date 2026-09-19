package com.attendance.course;

import com.attendance.course.sessions.TeachingSessionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class GroupManagementService {
    private final CourseOfferingRepository offerings;
    private final TeachingGroupRepository groups;
    private final EnrollmentRepository enrollments;
    private final CourseEmployeeRepository courseEmployees;
    private final TeachingSessionRepository sessions;

    public GroupManagementService(CourseOfferingRepository offerings, TeachingGroupRepository groups,
            EnrollmentRepository enrollments, CourseEmployeeRepository courseEmployees,
            TeachingSessionRepository sessions) {
        this.offerings = offerings;
        this.groups = groups;
        this.enrollments = enrollments;
        this.courseEmployees = courseEmployees;
        this.sessions = sessions;
    }

    public List<ActivityGroupSettingsResponse> findSettings(UUID offeringId, UUID employeeId) {
        CourseOffering offering = requireEmployeeOffering(offeringId, employeeId);
        return List.of(settings(offering, ActivityType.AUDITORY_EXERCISE),
                settings(offering, ActivityType.LABORATORY_EXERCISE));
    }

    @Transactional
    public ActivityGroupSettingsResponse configure(UUID offeringId, ActivityType activityType,
            GroupSettingsRequest request, UUID employeeId) {
        validateActivity(activityType);
        CourseOffering offering = requireEmployeeOffering(offeringId, employeeId);
        if (offering.getYearOfStudy() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ručno formiranje grupa je namijenjeno studentima starijih godina.");
        }
        List<Enrollment> offeringEnrollments = enrollments
                .findByOfferingIdOrderByStudentLastNameAscStudentFirstNameAscStudentIndexNumberAsc(offeringId);
        List<TeachingGroup> oldGroups = groups
                .findByOfferingIdAndActivityTypeOrderByGroupNumberAsc(offeringId, activityType);
        offeringEnrollments.forEach(enrollment -> enrollment.getGroups()
                .removeIf(group -> group.getActivityType() == activityType));
        enrollments.flush();
        List<UUID> oldGroupIds = oldGroups.stream().map(TeachingGroup::getId).toList();
        if (!oldGroupIds.isEmpty()) {
            sessions.deleteAll(sessions.findByGroupIdIn(oldGroupIds));
            sessions.flush();
            groups.deleteAll(oldGroups);
            groups.flush();
        }

        int studentCount = offeringEnrollments.size();
        if ((long) request.groupCount() * request.groupCapacity() < studentCount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ukupan kapacitet grupa mora biti dovoljan za sve studente.");
        }
        String prefix = activityType == ActivityType.AUDITORY_EXERCISE ? "G" : "L";
        for (int number = 1; number <= request.groupCount(); number++) {
            groups.save(new TeachingGroup(offering, activityType, prefix + number, request.groupCapacity()));
        }
        offering.setSelectionEnabled(activityType, request.selectionEnabled());
        offerings.saveAndFlush(offering);
        groups.flush();
        return settings(offering, activityType);
    }

    public List<StudentGroupSelectionResponse> findStudentChoices(UUID offeringId, UUID studentId) {
        CourseOffering offering = requireStudentOffering(offeringId, studentId);
        Enrollment enrollment = enrollments.findFirstByStudentIdAndOfferingId(studentId, offeringId).orElseThrow();
        return List.of(studentSettings(offering, enrollment, ActivityType.AUDITORY_EXERCISE),
                studentSettings(offering, enrollment, ActivityType.LABORATORY_EXERCISE));
    }

    @Transactional
    public StudentGroupSelectionResponse join(UUID offeringId, UUID groupId, UUID studentId) {
        CourseOffering offering = requireStudentOffering(offeringId, studentId);
        TeachingGroup selected = groups.findForUpdate(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupa nije pronađena."));
        if (!selected.getOffering().getId().equals(offeringId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupa ne pripada izabranom predmetu.");
        }
        validateActivity(selected.getActivityType());
        if (!offering.isSelectionEnabled(selected.getActivityType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prijava u ovu vrstu grupe nije omogućena.");
        }
        Enrollment enrollment = enrollments.findByStudentIdAndOfferingId(studentId, offeringId).orElseThrow();
        boolean alreadySelected = enrollment.getGroups().stream()
                .anyMatch(group -> group.getId().equals(groupId));
        if (!alreadySelected && enrollments.countByGroupsId(groupId) >= selected.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Izabrana grupa je popunjena.");
        }
        enrollment.getGroups().removeIf(group -> group.getActivityType() == selected.getActivityType()
                && !group.getId().equals(groupId));
        enrollment.getGroups().add(selected);
        enrollments.flush();
        return studentSettings(offering, enrollment, selected.getActivityType());
    }

    private ActivityGroupSettingsResponse settings(CourseOffering offering, ActivityType activityType) {
        List<Enrollment> students = enrollments
                .findByOfferingIdOrderByStudentLastNameAscStudentFirstNameAscStudentIndexNumberAsc(offering.getId());
        List<ManagedGroupResponse> managedGroups = managedGroups(offering.getId(), activityType);
        return new ActivityGroupSettingsResponse(activityType, students.size(), !managedGroups.isEmpty(),
                offering.isSelectionEnabled(activityType), managedGroups);
    }

    private StudentGroupSelectionResponse studentSettings(CourseOffering offering, Enrollment enrollment,
            ActivityType activityType) {
        UUID currentGroupId = enrollment.getGroups().stream()
                .filter(group -> group.getActivityType() == activityType)
                .map(TeachingGroup::getId).findFirst().orElse(null);
        return new StudentGroupSelectionResponse(activityType, offering.isSelectionEnabled(activityType),
                currentGroupId, managedGroups(offering.getId(), activityType));
    }

    private List<ManagedGroupResponse> managedGroups(UUID offeringId, ActivityType activityType) {
        return groups.findByOfferingIdAndActivityTypeOrderByGroupNumberAsc(offeringId, activityType).stream()
                .filter(group -> group.getCapacity() != null)
                .sorted(Comparator.comparingInt(group -> groupNumber(group.getGroupNumber())))
                .map(group -> new ManagedGroupResponse(group.getId(), group.getGroupNumber(), group.getCapacity(),
                        enrollments.countByGroupsId(group.getId())))
                .toList();
    }

    private CourseOffering requireEmployeeOffering(UUID offeringId, UUID employeeId) {
        CourseOffering offering = requireOffering(offeringId);
        if (!courseEmployees.existsByCourseIdAndEmployeeIdAndRole(offering.getCourse().getId(), employeeId,
                CourseEmployeeRole.TEACHER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Samo profesor povezan sa predmetom može podešavati grupe.");
        }
        return offering;
    }

    private CourseOffering requireStudentOffering(UUID offeringId, UUID studentId) {
        CourseOffering offering = requireOffering(offeringId);
        if (!enrollments.existsByStudentIdAndOfferingId(studentId, offeringId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Student nije upisan na ovaj predmet.");
        }
        return offering;
    }

    private CourseOffering requireOffering(UUID offeringId) {
        return offerings.findById(offeringId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Izvođenje predmeta nije pronađeno."));
    }

    private void validateActivity(ActivityType activityType) {
        if (activityType == ActivityType.LECTURE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Izbor grupa je moguć samo za auditorne i laboratorijske vježbe.");
        }
    }

    private int groupNumber(String value) {
        try {
            return Integer.parseInt(value.substring(1));
        } catch (RuntimeException exception) {
            return Integer.MAX_VALUE;
        }
    }
}
