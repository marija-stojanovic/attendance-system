ALTER TABLE course_employees ADD COLUMN employee_email VARCHAR(255);
ALTER TABLE course_employees ADD COLUMN employee_username VARCHAR(100);
ALTER TABLE course_employees ADD COLUMN employee_display_name VARCHAR(200);

UPDATE course_employees assignment
SET employee_email = employee.email,
    employee_username = employee.username,
    employee_display_name = employee.display_name
FROM local_users employee
WHERE assignment.employee_id = employee.id;

ALTER TABLE course_employees ALTER COLUMN employee_email SET NOT NULL;
ALTER TABLE course_employees ALTER COLUMN employee_username SET NOT NULL;
ALTER TABLE course_employees ALTER COLUMN employee_display_name SET NOT NULL;
ALTER TABLE course_employees DROP CONSTRAINT IF EXISTS course_employees_employee_id_fkey;
