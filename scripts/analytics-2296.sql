\set ON_ERROR_STOP on

BEGIN;

DO $$
DECLARE
    offering_id UUID;
BEGIN
    SELECT o.id INTO offering_id
    FROM course_offerings o
    JOIN courses c ON c.id = o.course_id
    WHERE c.code = '2296' AND o.academic_year = '2025/2026'
      AND o.study_program = 'Рачунарство и информатика'
      AND o.study_type = '1.Ц.А' AND o.year_of_study = 4 AND o.semester = 8;

    IF offering_id IS NULL THEN
        RAISE EXCEPTION 'Prvo uvezite predmet 2296 za 2025/2026.';
    END IF;
    IF (SELECT count(*) FROM enrollments WHERE course_offering_id = offering_id) <> 60 THEN
        RAISE EXCEPTION 'Predmet 2296 mora imati 60 studenata iz Excel fajla.';
    END IF;
    IF (SELECT count(*) FROM teaching_groups
        WHERE course_offering_id = offering_id AND activity_type <> 'LECTURE') <> 5
       OR (SELECT count(*) FROM teaching_groups
           WHERE course_offering_id = offering_id AND
           ((activity_type = 'AUDITORY_EXERCISE' AND group_number IN ('G1', 'G2') AND capacity = 30)
            OR (activity_type = 'LABORATORY_EXERCISE' AND group_number IN ('L1', 'L2', 'L3') AND capacity = 20))) <> 5 THEN
        RAISE EXCEPTION 'Potrebne su G1 i G2 (po 30) te L1, L2 i L3 (po 20).';
    END IF;
END $$;

INSERT INTO teaching_groups (id, course_offering_id, activity_type, group_number)
SELECT md5('analytics-2296-lecture:' || o.id)::uuid, o.id, 'LECTURE', 'Svi'
FROM course_offerings o
JOIN courses c ON c.id = o.course_id
WHERE c.code = '2296' AND o.academic_year = '2025/2026'
ON CONFLICT (course_offering_id, activity_type, group_number) DO NOTHING;

WITH ranked AS (
    SELECT e.id AS enrollment_id, row_number() OVER (ORDER BY s.index_number) AS position
    FROM enrollments e
    JOIN students s ON s.id = e.student_id
    JOIN course_offerings o ON o.id = e.course_offering_id
    JOIN courses c ON c.id = o.course_id
    WHERE c.code = '2296' AND o.academic_year = '2025/2026'
), assignments AS (
    SELECT enrollment_id, 'AUDITORY_EXERCISE' AS activity_type,
           CASE WHEN position <= 30 THEN 'G1' ELSE 'G2' END AS group_number
    FROM ranked
    UNION ALL
    SELECT enrollment_id, 'LABORATORY_EXERCISE',
           'L' || (((position - 1) % 3) + 1)::text
    FROM ranked
)
INSERT INTO enrollment_groups (enrollment_id, teaching_group_id)
SELECT a.enrollment_id, g.id
FROM assignments a
JOIN enrollments e ON e.id = a.enrollment_id
JOIN teaching_groups g ON g.course_offering_id = e.course_offering_id
    AND g.activity_type = a.activity_type AND g.group_number = a.group_number
WHERE NOT EXISTS (
    SELECT 1 FROM enrollment_groups eg
    JOIN teaching_groups existing ON existing.id = eg.teaching_group_id
    WHERE eg.enrollment_id = a.enrollment_id AND existing.activity_type = a.activity_type
)
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    offering_id UUID;
BEGIN
    SELECT o.id INTO offering_id FROM course_offerings o
    JOIN courses c ON c.id = o.course_id
    WHERE c.code = '2296' AND o.academic_year = '2025/2026';

    IF EXISTS (
        SELECT 1 FROM enrollments e
        LEFT JOIN enrollment_groups eg ON eg.enrollment_id = e.id
        LEFT JOIN teaching_groups g ON g.id = eg.teaching_group_id
        WHERE e.course_offering_id = offering_id
        GROUP BY e.id
        HAVING count(*) FILTER (WHERE g.activity_type = 'AUDITORY_EXERCISE') <> 1
            OR count(*) FILTER (WHERE g.activity_type = 'LABORATORY_EXERCISE') <> 1
    ) OR EXISTS (
        SELECT 1 FROM teaching_groups g
        LEFT JOIN enrollment_groups eg ON eg.teaching_group_id = g.id
        WHERE g.course_offering_id = offering_id
        GROUP BY g.id
        HAVING count(eg.enrollment_id) > g.capacity
    ) THEN
        RAISE EXCEPTION 'Raspored nije potpun ili je prekoračen kapacitet grupe.';
    END IF;
END $$;

WITH old_sessions AS (
    SELECT md5('analytics-2296-session:' || g.id || ':' || w.week_number)::uuid AS id
    FROM teaching_groups g
    JOIN course_offerings o ON o.id = g.course_offering_id
    JOIN courses c ON c.id = o.course_id
    CROSS JOIN generate_series(1, 9, 2) AS w(week_number)
    WHERE c.code = '2296' AND o.academic_year = '2025/2026'
      AND g.activity_type IN ('AUDITORY_EXERCISE', 'LABORATORY_EXERCISE')
)
DELETE FROM attendances a USING old_sessions s WHERE a.teaching_session_id = s.id;

WITH old_sessions AS (
    SELECT md5('analytics-2296-session:' || g.id || ':' || w.week_number)::uuid AS id
    FROM teaching_groups g
    JOIN course_offerings o ON o.id = g.course_offering_id
    JOIN courses c ON c.id = o.course_id
    CROSS JOIN generate_series(1, 9, 2) AS w(week_number)
    WHERE c.code = '2296' AND o.academic_year = '2025/2026'
      AND g.activity_type IN ('AUDITORY_EXERCISE', 'LABORATORY_EXERCISE')
)
DELETE FROM teaching_sessions ts USING old_sessions s WHERE ts.id = s.id;

WITH weeks AS (SELECT generate_series(0, 8, 2) AS week_number)
INSERT INTO teaching_sessions (id, teaching_group_id, session_date, start_time, status)
SELECT md5('analytics-2296-session:' || g.id || ':' || w.week_number)::uuid,
       g.id,
       CASE WHEN g.activity_type = 'LABORATORY_EXERCISE'
            THEN DATE '2026-03-05' + w.week_number * 7
            ELSE DATE '2026-03-02' + w.week_number * 7 END,
       CASE g.activity_type WHEN 'LECTURE' THEN TIME '08:15'
            WHEN 'AUDITORY_EXERCISE' THEN TIME '10:15' ELSE TIME '13:15' END,
       'CLOSED'
FROM teaching_groups g
JOIN course_offerings o ON o.id = g.course_offering_id
JOIN courses c ON c.id = o.course_id
CROSS JOIN weeks w
WHERE c.code = '2296' AND o.academic_year = '2025/2026'
  AND (g.activity_type IN ('AUDITORY_EXERCISE', 'LABORATORY_EXERCISE')
       OR (g.activity_type = 'LECTURE' AND g.group_number = 'Svi'))
ON CONFLICT (id) DO NOTHING;

WITH ranked AS (
    SELECT e.id AS enrollment_id, e.course_offering_id, s.id AS student_id, s.index_number,
           row_number() OVER (ORDER BY s.index_number) AS position
    FROM enrollments e
    JOIN students s ON s.id = e.student_id
    JOIN course_offerings o ON o.id = e.course_offering_id
    JOIN courses c ON c.id = o.course_id
    WHERE c.code = '2296' AND o.academic_year = '2025/2026'
), weeks AS (SELECT generate_series(0, 8, 2) AS week_number), candidates AS (
    SELECT r.student_id, r.index_number, r.position, w.week_number,
           ts.id AS session_id, ts.session_date, ts.start_time,
           CASE WHEN r.position IN (1, 2) THEN 0
                WHEN r.position % 12 = 0 THEN 25
                WHEN r.position % 7 = 0 THEN 45
                WHEN r.position % 5 = 0 THEN 60
                WHEN r.position % 3 = 0 THEN 75
                ELSE 90 END AS base_percent
    FROM ranked r
    JOIN teaching_groups g ON g.course_offering_id = r.course_offering_id
      AND (g.activity_type = 'LECTURE' AND g.group_number = 'Svi'
           OR EXISTS (SELECT 1 FROM enrollment_groups eg
                      WHERE eg.enrollment_id = r.enrollment_id AND eg.teaching_group_id = g.id))
    CROSS JOIN weeks w
    JOIN teaching_sessions ts ON ts.id =
        md5('analytics-2296-session:' || g.id || ':' || w.week_number)::uuid
)
INSERT INTO attendances (id, teaching_session_id, student_id, recorded_at,
                         ip_address, user_agent, request_id)
SELECT md5('analytics-2296-attendance:' || c.session_id || ':' || c.student_id)::uuid,
       c.session_id, c.student_id,
       (c.session_date + c.start_time) AT TIME ZONE 'Europe/Sarajevo' + INTERVAL '7 minutes',
       '127.0.0.1', 'analytics-demo-2296',
       md5('analytics-2296-request:' || c.session_id || ':' || c.student_id)::uuid
FROM candidates c
WHERE mod(abs(hashtext(c.index_number || ':' || c.session_id)::bigint), 100) <
      c.base_percent + CASE WHEN c.week_number < 3 THEN 8
                            WHEN c.week_number >= 7 THEN -8 ELSE 0 END
ON CONFLICT (teaching_session_id, student_id) DO NOTHING;

COMMIT;

SELECT g.activity_type, g.group_number,
       CASE WHEN g.activity_type = 'LECTURE'
            THEN count(DISTINCT e.id)
            ELSE count(DISTINCT eg.enrollment_id) END AS students,
       count(DISTINCT ts.id) AS sessions, count(DISTINCT a.id) AS attendances
FROM teaching_groups g
JOIN course_offerings o ON o.id = g.course_offering_id
JOIN courses c ON c.id = o.course_id
LEFT JOIN enrollment_groups eg ON eg.teaching_group_id = g.id
LEFT JOIN enrollments e ON e.course_offering_id = g.course_offering_id
LEFT JOIN teaching_sessions ts ON ts.teaching_group_id = g.id
LEFT JOIN attendances a ON a.teaching_session_id = ts.id
WHERE c.code = '2296' AND o.academic_year = '2025/2026'
GROUP BY g.activity_type, g.group_number
ORDER BY g.activity_type, g.group_number;
