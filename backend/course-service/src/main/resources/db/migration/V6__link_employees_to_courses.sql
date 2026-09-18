CREATE TABLE course_employees (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES local_users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('TEACHER', 'ASSISTANT')),
    CONSTRAINT uq_course_employee UNIQUE (course_id, employee_id)
);

INSERT INTO course_employees (id, course_id, employee_id, role)
SELECT gen_random_uuid(), courses.id, local_users.id, 'TEACHER'
FROM courses CROSS JOIN local_users;
