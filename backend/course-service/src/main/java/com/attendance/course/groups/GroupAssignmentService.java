package com.attendance.course.groups;

import com.attendance.course.ActivityType;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(GroupAssignmentProperties.class)
public class GroupAssignmentService {
    private static final Pattern INDEX = Pattern.compile("^(?<number>[0-9]+)/(?<cohort>[0-9]{2})$");

    private final GroupAssignmentProperties properties;

    public GroupAssignmentService(GroupAssignmentProperties properties) {
        this.properties = properties;
    }

    public Assignment assign(Short yearOfStudy, ActivityType activityType, String importedGroup, String indexNumber) {
        if (yearOfStudy == null || activityType == null) {
            return new Assignment(null, null);
        }
        if (yearOfStudy != 1) {
            return new Assignment(importedGroup, null);
        }
        if (activityType == ActivityType.LECTURE) {
            return new Assignment(properties.lectureGroup(), null);
        }

        String normalizedIndex = indexNumber == null ? "" : indexNumber.strip().replace(" ", "");
        var match = INDEX.matcher(normalizedIndex);
        if (!match.matches()) {
            return missing("Broj indeksa nije u očekivanom formatu.");
        }
        var cohort = properties.cohorts().get(match.group("cohort"));
        if (cohort == null) {
            return missing("Za godinu upisa /" + match.group("cohort") + " nisu podešena pravila grupa.");
        }

        List<GroupAssignmentProperties.GroupRule> rules = activityType == ActivityType.AUDITORY_EXERCISE
                ? cohort.auditory() : cohort.laboratory();
        String number = match.group("number");
        for (var rule : rules) {
            for (var range : rule.ranges()) {
                if (!number.startsWith(range.prefix()) || number.length() == range.prefix().length()) {
                    continue;
                }
                String ordinalText = number.substring(range.prefix().length());
                if (!ordinalText.chars().allMatch(Character::isDigit)) {
                    continue;
                }
                try {
                    int ordinal = Integer.parseInt(ordinalText);
                    if (ordinal >= range.from() && ordinal <= range.to()) {
                        return new Assignment(rule.group(), null);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String type = activityType == ActivityType.AUDITORY_EXERCISE ? "auditorna" : "laboratorijska";
        return missing("Za broj indeksa " + indexNumber + " nije određena " + type + " grupa.");
    }

    private Assignment missing(String message) {
        return new Assignment(null, message);
    }

    public record Assignment(String groupNumber, String note) {
    }
}
