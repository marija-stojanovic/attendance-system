package com.attendance.course.imports;

import com.attendance.course.ApiExceptionHandler;
import com.attendance.course.StudentEmailGenerator;
import com.attendance.course.groups.GroupAssignmentService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ImportPreviewController.class, properties = {
        "course-import.layouts.other.metadata-value-offset=1",
        "course-import.layouts.other.aliases.courseCode[0]=ID predmet"
})
@Import({ImportPreviewService.class, ImportFileReader.class, ImportParser.class, StudentEmailGenerator.class,
        GroupAssignmentService.class, ApiExceptionHandler.class})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class ImportPreviewControllerTests {
    @Autowired private MockMvc mvc;

    @Test
    void previewsRepeatedUploadsWithoutDatabaseAccess() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(multipart("/api/imports/preview").file(csv(ImportParserTests.CSV)))
                    .andExpect(status().isOk()).andExpect(jsonPath("valid").value(true))
                    .andExpect(jsonPath("students.length()").value(2))
                    .andExpect(jsonPath("metadata.courseCode").value("DEMO-101"));
        }
    }

    @Test
    void bindsCustomLayoutConfiguration() throws Exception {
        mvc.perform(multipart("/api/imports/preview").file(csv(ImportParserTests.CSV.replace("Шифра", "ID predmet")))
                        .param("layout", "other"))
                .andExpect(status().isOk()).andExpect(jsonPath("metadata.courseCode").value("DEMO-101"));
    }

    @Test
    void returnsRowErrorsWith422() throws Exception {
        mvc.perform(multipart("/api/imports/preview").file(csv(ImportParserTests.CSV.replace("Измишљић;Јана", "Измишљић;"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("valid").value(false))
                .andExpect(jsonPath("errors[0].row").value(5)).andExpect(jsonPath("students").isEmpty());
    }

    @Test
    void requiresFileAndKnownLayout() throws Exception {
        mvc.perform(multipart("/api/imports/preview")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/imports/preview").file(csv(ImportParserTests.CSV)).param("layout", "unknown"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnsupportedAndOversizedFiles() throws Exception {
        mvc.perform(multipart("/api/imports/preview").file(new MockMultipartFile("file", "test.xls", "application/octet-stream", new byte[]{1})))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/imports/preview").file(new MockMultipartFile("file", "test.csv", "text/csv",
                        new byte[(int) ImportFileReader.MAX_BYTES + 1])))
                .andExpect(status().isPayloadTooLarge());
    }

    private MockMultipartFile csv(String text) {
        return new MockMultipartFile("file", "test.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }
}
