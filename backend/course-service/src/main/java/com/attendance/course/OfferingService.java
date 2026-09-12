package com.attendance.course;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class OfferingService {
    private final CourseRepository courses;
    private final CourseOfferingRepository offerings;
    private final TeachingGroupRepository groups;

    public OfferingService(CourseRepository courses, CourseOfferingRepository offerings, TeachingGroupRepository groups) {
        this.courses = courses;
        this.offerings = offerings;
        this.groups = groups;
    }

    @Transactional
    public OfferingResponse create(UUID courseId, CreateOfferingRequest request) {
        String[] years = request.academicYear().split("/");
        if (Integer.parseInt(years[1]) != Integer.parseInt(years[0]) + 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Akademska godina mora sadržati dvije uzastopne godine.");
        }
        Course course = courses.findById(courseId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Predmet nije pronađen."));
        return OfferingResponse.from(offerings.saveAndFlush(new CourseOffering(course, request)));
    }

    public List<OfferingResponse> findByCourse(UUID courseId) {
        if (!courses.existsById(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Predmet nije pronađen.");
        }
        return offerings.findByCourseIdOrderByAcademicYearDescStudyProgramAscStudyTypeAsc(courseId).stream()
                .map(OfferingResponse::from).toList();
    }

    public OfferingResponse findById(UUID id) {
        return OfferingResponse.from(requireOffering(id));
    }

    @Transactional
    public GroupResponse createGroup(UUID offeringId, CreateGroupRequest request) {
        return GroupResponse.from(groups.saveAndFlush(new TeachingGroup(requireOffering(offeringId),
                request.activityType(), request.groupNumber())));
    }

    public List<GroupResponse> findGroups(UUID offeringId) {
        requireOffering(offeringId);
        return groups.findByOfferingIdOrderByActivityTypeAscGroupNumberAsc(offeringId).stream()
                .map(GroupResponse::from).toList();
    }

    public GroupResponse findGroup(UUID id) {
        return GroupResponse.from(groups.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupa nije pronađena.")));
    }

    private CourseOffering requireOffering(UUID id) {
        return offerings.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Izvođenje predmeta nije pronađeno."));
    }
}
