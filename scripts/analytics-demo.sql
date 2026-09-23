DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM local_users WHERE username = 'profesor') THEN
        RAISE EXCEPTION 'Prvo pokrenite aplikaciju da bi testni nalog profesor bio kreiran.';
    END IF;
END $$;

INSERT INTO courses (id, code, name)
VALUES ('90000000-0000-0000-0000-000000000001', 'DEMO-AN', 'Demonstraciona analitika')
ON CONFLICT (code) DO NOTHING;

INSERT INTO course_offerings (id, course_id, academic_year, study_program, study_type, year_of_study, semester)
VALUES ('91000000-0000-0000-0000-000000000001', '90000000-0000-0000-0000-000000000001',
        '2026/2027', 'Računarstvo i informatika', 'OSNOVNE', 1, 1)
ON CONFLICT (course_id, academic_year, study_program, study_type) DO NOTHING;

INSERT INTO course_employees (id, course_id, employee_id, role)
SELECT '93000000-0000-0000-0000-000000000001', '90000000-0000-0000-0000-000000000001', id, 'TEACHER'
FROM local_users WHERE username = 'profesor'
ON CONFLICT (course_id, employee_id) DO NOTHING;

INSERT INTO teaching_groups (id, course_offering_id, activity_type, group_number, capacity)
VALUES
    ('92000000-0000-0000-0000-000000000001', '91000000-0000-0000-0000-000000000001', 'LECTURE', 'ALL', NULL),
    ('92000000-0000-0000-0000-000000000002', '91000000-0000-0000-0000-000000000001', 'AUDITORY_EXERCISE', 'G1', 30),
    ('92000000-0000-0000-0000-000000000003', '91000000-0000-0000-0000-000000000001', 'AUDITORY_EXERCISE', 'G2', 30)
ON CONFLICT (course_offering_id, activity_type, group_number) DO NOTHING;

INSERT INTO students (id, index_number, first_name, last_name, institutional_email)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'DEMO-01/26', 'Ana', 'Anić', 'ana.anic.demo@student.etf.unibl.org'),
    ('a0000000-0000-0000-0000-000000000002', 'DEMO-02/26', 'Bojan', 'Babić', 'bojan.babic.demo@student.etf.unibl.org'),
    ('a0000000-0000-0000-0000-000000000003', 'DEMO-03/26', 'Ceca', 'Cvijić', 'ceca.cvijic.demo@student.etf.unibl.org'),
    ('a0000000-0000-0000-0000-000000000004', 'DEMO-04/26', 'Darko', 'Delić', 'darko.delic.demo@student.etf.unibl.org'),
    ('a0000000-0000-0000-0000-000000000005', 'DEMO-05/26', 'Ena', 'Erić', 'ena.eric.demo@student.etf.unibl.org'),
    ('a0000000-0000-0000-0000-000000000006', 'DEMO-06/26', 'Filip', 'Filić', 'filip.filic.demo@student.etf.unibl.org'),
    ('a0000000-0000-0000-0000-000000000007', 'DEMO-07/26', 'Goran', 'Gajić', 'goran.gajic.demo@student.etf.unibl.org'),
    ('a0000000-0000-0000-0000-000000000008', 'DEMO-08/26', 'Hana', 'Hadžić', 'hana.hadzic.demo@student.etf.unibl.org')
ON CONFLICT (index_number) DO NOTHING;

INSERT INTO enrollments (id, student_id, course_offering_id, status)
SELECT ('b0000000-0000-0000-0000-' || lpad(number::text, 12, '0'))::uuid,
       ('a0000000-0000-0000-0000-' || lpad(number::text, 12, '0'))::uuid,
       '91000000-0000-0000-0000-000000000001', 'REDOVAN'
FROM generate_series(1, 8) AS number
ON CONFLICT (student_id, course_offering_id) DO NOTHING;

INSERT INTO enrollment_groups (enrollment_id, teaching_group_id)
SELECT ('b0000000-0000-0000-0000-' || lpad(number::text, 12, '0'))::uuid,
       CASE WHEN number <= 4 THEN '92000000-0000-0000-0000-000000000002'::uuid
            ELSE '92000000-0000-0000-0000-000000000003'::uuid END
FROM generate_series(1, 8) AS number
ON CONFLICT DO NOTHING;

INSERT INTO teaching_sessions (id, teaching_group_id, session_date, start_time, status, qr_started_at)
VALUES
    ('c0000000-0000-0000-0000-000000000001', '92000000-0000-0000-0000-000000000001', '2026-09-15', '09:00', 'CLOSED', '2026-09-15 09:00:00+00'),
    ('c0000000-0000-0000-0000-000000000002', '92000000-0000-0000-0000-000000000002', '2026-09-22', '10:00', 'CLOSED', '2026-09-22 10:00:00+00'),
    ('c0000000-0000-0000-0000-000000000003', '92000000-0000-0000-0000-000000000003', '2026-09-24', '10:00', 'CLOSED', '2026-09-24 10:00:00+00'),
    ('c0000000-0000-0000-0000-000000000004', '92000000-0000-0000-0000-000000000001', '2026-10-13', '09:00', 'CLOSED', '2026-10-13 09:00:00+00'),
    ('c0000000-0000-0000-0000-000000000005', '92000000-0000-0000-0000-000000000002', '2026-10-20', '10:00', 'CLOSED', '2026-10-20 10:00:00+00'),
    ('c0000000-0000-0000-0000-000000000006', '92000000-0000-0000-0000-000000000003', '2026-10-22', '10:00', 'CLOSED', '2026-10-22 10:00:00+00'),
    ('c0000000-0000-0000-0000-000000000007', '92000000-0000-0000-0000-000000000001', '2026-11-10', '09:00', 'CLOSED', '2026-11-10 09:00:00+00'),
    ('c0000000-0000-0000-0000-000000000008', '92000000-0000-0000-0000-000000000002', '2026-11-17', '10:00', 'CLOSED', '2026-11-17 10:00:00+00'),
    ('c0000000-0000-0000-0000-000000000009', '92000000-0000-0000-0000-000000000003', '2026-11-19', '10:00', 'CLOSED', '2026-11-19 10:00:00+00')
ON CONFLICT (id) DO NOTHING;

WITH attendance_map(session_number, student_number) AS (
    VALUES
        (1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),
        (2,1),(2,2),(2,3),(2,4),(3,5),(3,6),(3,7),
        (4,1),(4,2),(4,3),(4,5),(4,6),(4,7),
        (5,1),(5,2),(5,3),(6,5),(6,6),
        (7,1),(7,2),(7,5),(7,6),(7,8),
        (8,1),(8,2),(9,5)
)
INSERT INTO attendances (id, teaching_session_id, student_id, recorded_at, ip_address, user_agent, request_id)
SELECT gen_random_uuid(),
       ('c0000000-0000-0000-0000-' || lpad(session_number::text, 12, '0'))::uuid,
       ('a0000000-0000-0000-0000-' || lpad(student_number::text, 12, '0'))::uuid,
       session_date + start_time + interval '5 minutes', '127.0.0.1', 'Demonstracioni podaci', gen_random_uuid()
FROM attendance_map
JOIN teaching_sessions ON id = ('c0000000-0000-0000-0000-' || lpad(session_number::text, 12, '0'))::uuid
ON CONFLICT (teaching_session_id, student_id) DO NOTHING;
