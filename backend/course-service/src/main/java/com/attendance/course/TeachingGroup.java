package com.attendance.course;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "teaching_groups", uniqueConstraints = @UniqueConstraint(name = "uq_teaching_group",
        columnNames = {"course_offering_id", "activity_type", "group_number"}))
public class TeachingGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false)
    private CourseOffering offering;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    private ActivityType activityType;

    @Column(name = "group_number", nullable = false, length = 20)
    private String groupNumber;

    @Column
    private Integer capacity;

    protected TeachingGroup() {
    }

    public TeachingGroup(CourseOffering offering, ActivityType activityType, String groupNumber) {
        this(offering, activityType, groupNumber, null);
    }

    public TeachingGroup(CourseOffering offering, ActivityType activityType, String groupNumber, Integer capacity) {
        this.offering = offering;
        this.activityType = activityType;
        this.groupNumber = groupNumber;
        this.capacity = capacity;
    }

    public UUID getId() { return id; }
    public CourseOffering getOffering() { return offering; }
    public ActivityType getActivityType() { return activityType; }
    public String getGroupNumber() { return groupNumber; }
    public Integer getCapacity() { return capacity; }
}
