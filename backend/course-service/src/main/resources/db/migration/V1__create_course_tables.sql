CREATE TABLE courses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE course_offerings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL REFERENCES courses(id),
    academic_year VARCHAR(9) NOT NULL,
    study_program TEXT NOT NULL,
    study_type VARCHAR(100) NOT NULL,
    year_of_study SMALLINT NOT NULL CHECK (year_of_study > 0),
    semester SMALLINT NOT NULL CHECK (semester > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_course_offering UNIQUE (course_id, academic_year, study_program, study_type)
);

CREATE TABLE students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    index_number VARCHAR(30) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    institutional_email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE teaching_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_offering_id UUID NOT NULL REFERENCES course_offerings(id) ON DELETE CASCADE,
    activity_type VARCHAR(30) NOT NULL CHECK (
        activity_type IN ('LECTURE', 'AUDITORY_EXERCISE', 'LABORATORY_EXERCISE')
    ),
    group_number VARCHAR(20) NOT NULL,
    CONSTRAINT uq_teaching_group UNIQUE (course_offering_id, activity_type, group_number)
);

CREATE TABLE enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id),
    course_offering_id UUID NOT NULL REFERENCES course_offerings(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_enrollment UNIQUE (student_id, course_offering_id)
);

CREATE TABLE enrollment_groups (
    enrollment_id UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    teaching_group_id UUID NOT NULL REFERENCES teaching_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (enrollment_id, teaching_group_id)
);

CREATE INDEX idx_course_offerings_course_id ON course_offerings(course_id);
CREATE INDEX idx_teaching_groups_offering_id ON teaching_groups(course_offering_id);
CREATE INDEX idx_enrollments_offering_id ON enrollments(course_offering_id);
