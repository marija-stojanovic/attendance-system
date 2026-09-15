package com.attendance.course.groups;

import com.attendance.course.ActivityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class GroupAssignmentServiceTests {
    @Autowired private GroupAssignmentService assignments;

    @ParameterizedTest
    @CsvSource({
            "1101/25,G1", "1159/25,G1", "1160/25,G2", "11117/25,G2",
            "11118/25,G3", "11123/25,G3", "1201/25,G3", "1252/25,G3",
            "1253/25,G4", "1255/25,G4", "1301/25,G4", "1353/25,G4"
    })
    void assignsAuditoryGroupsAtRangeBoundaries(String index, String expected) {
        assertThat(assignments.assign((short) 1, ActivityType.AUDITORY_EXERCISE, "1", index).groupNumber())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "1101/25,L1", "1121/25,L1", "1122/25,L2", "1144/25,L2",
            "1145/25,L3", "1165/25,L3", "1166/25,L4", "1188/25,L4",
            "1189/25,L5", "11108/25,L5", "11109/25,L6", "11123/25,L6",
            "1201/25,L6", "1206/25,L6", "1207/25,L7", "1226/25,L7",
            "1227/25,L8", "1249/25,L8", "1251/25,L9", "1255/25,L9",
            "1301/25,L9", "1316/25,L9", "1317/25,L10", "1337/25,L10",
            "1338/25,L11", "1353/25,L11"
    })
    void assignsLaboratoryGroupsAtRangeBoundaries(String index, String expected) {
        assertThat(assignments.assign((short) 1, ActivityType.LABORATORY_EXERCISE, "1", index).groupNumber())
                .isEqualTo(expected);
    }

    @Test
    void keepsImportedGroupOutsideFirstYear() {
        var result = assignments.assign((short) 4, ActivityType.LABORATORY_EXERCISE, "1", "11107/21");
        assertThat(result.groupNumber()).isEqualTo("1");
        assertThat(result.note()).isNull();
    }

    @Test
    void usesOneGroupForFirstYearLectures() {
        assertThat(assignments.assign((short) 1, ActivityType.LECTURE, "1", "1250/25").groupNumber())
                .isEqualTo("ALL");
    }

    @Test
    void leavesNoteForUncoveredIndex() {
        var result = assignments.assign((short) 1, ActivityType.LABORATORY_EXERCISE, "1", "1250/25");
        assertThat(result.groupNumber()).isNull();
        assertThat(result.note()).contains("1250/25");
    }

    @Test
    void leavesNoteWhenCohortHasNoRules() {
        var result = assignments.assign((short) 1, ActivityType.AUDITORY_EXERCISE, "1", "1101/24");
        assertThat(result.groupNumber()).isNull();
        assertThat(result.note()).contains("/24");
    }
}
