package com.attendance.course.imports;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("course-import")
public record ImportProperties(@DefaultValue("5000") int maxRows,
                               @DefaultValue("64") int maxColumns,
                               @DefaultValue("100") int headerScanRows,
                               Map<String, Layout> layouts) {
    public ImportProperties {
        if (maxRows < 1 || maxRows > 10000 || maxColumns < 1 || maxColumns > 256
                || headerScanRows < 1 || headerScanRows > maxRows) {
            throw new IllegalArgumentException("Ograničenja parsera nisu ispravna.");
        }
        layouts = layouts == null ? Map.of() : Map.copyOf(layouts);
    }

    public record Layout(String sheetName, Integer metadataHeaderRow, Integer studentHeaderRow,
                         @DefaultValue("1") int metadataValueOffset,
                         Map<String, Integer> metadataColumns, Map<String, Integer> studentColumns,
                         Map<String, List<String>> aliases) {
        public Layout {
            if (metadataValueOffset < 1 || (metadataHeaderRow != null && metadataHeaderRow < 1)
                    || (studentHeaderRow != null && studentHeaderRow < 1)) {
                throw new IllegalArgumentException("Redovi uvoznog rasporeda moraju biti pozitivni.");
            }
            metadataColumns = metadataColumns == null ? Map.of() : Map.copyOf(metadataColumns);
            studentColumns = studentColumns == null ? Map.of() : Map.copyOf(studentColumns);
            aliases = aliases == null ? Map.of() : Map.copyOf(aliases);
            for (Integer column : java.util.stream.Stream.concat(metadataColumns.values().stream(),
                    studentColumns.values().stream()).toList()) {
                if (column < 1) {
                    throw new IllegalArgumentException("Brojevi kolona moraju biti pozitivni.");
                }
            }
        }
    }
}
