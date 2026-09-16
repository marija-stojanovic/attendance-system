INSERT INTO students (index_number, first_name, last_name, institutional_email)
VALUES ('DEMO-MARIJA', 'Marija', 'Test', 'marija@student.etf.org'),
       ('DEMO-DEJAN', 'Dejan', 'Test', 'dejan@student.etf.org')
ON CONFLICT DO NOTHING;
