package com.attendance.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AnalyticsSnapshotRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void save(UUID offeringId, AnalyticsData.Source source) {
        try {
            jdbc.update("""
                    INSERT INTO analytics_snapshots (offering_id, source_data, updated_at)
                    VALUES (?, CAST(? AS jsonb), CURRENT_TIMESTAMP)
                    ON CONFLICT (offering_id) DO UPDATE
                    SET source_data = EXCLUDED.source_data, updated_at = CURRENT_TIMESTAMP
                    """, offeringId, json.writeValueAsString(source));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Podaci analitike se ne mogu sačuvati.", error);
        }
    }

    public AnalyticsData.Source load(UUID offeringId) {
        String source = jdbc.queryForObject(
                "SELECT source_data::text FROM analytics_snapshots WHERE offering_id = ?",
                String.class, offeringId);
        try {
            return json.readValue(source, AnalyticsData.Source.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Sačuvani podaci analitike nisu ispravni.", error);
        }
    }
}
