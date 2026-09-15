package com.attendance.course.groups;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("group-assignment")
public record GroupAssignmentProperties(@DefaultValue("ALL") String lectureGroup,
                                        Map<String, CohortRules> cohorts) {
    public GroupAssignmentProperties {
        lectureGroup = lectureGroup == null || lectureGroup.isBlank() ? "ALL" : lectureGroup.strip();
        cohorts = cohorts == null ? Map.of() : Map.copyOf(cohorts);
    }

    public record CohortRules(List<GroupRule> auditory, List<GroupRule> laboratory) {
        public CohortRules {
            auditory = auditory == null ? List.of() : List.copyOf(auditory);
            laboratory = laboratory == null ? List.of() : List.copyOf(laboratory);
        }
    }

    public record GroupRule(String group, List<IndexRange> ranges) {
        public GroupRule {
            group = group == null ? "" : group.strip();
            ranges = ranges == null ? List.of() : List.copyOf(ranges);
        }
    }

    public record IndexRange(String prefix, int from, int to) {
        public IndexRange {
            prefix = prefix == null ? "" : prefix.strip();
            if (prefix.isEmpty() || !prefix.chars().allMatch(Character::isDigit) || from < 1 || to < from) {
                throw new IllegalArgumentException("Raspon indeksa za određivanje grupe nije ispravan.");
            }
        }
    }
}
