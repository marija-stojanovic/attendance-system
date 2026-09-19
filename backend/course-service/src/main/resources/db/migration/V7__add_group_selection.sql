ALTER TABLE course_offerings
    ADD COLUMN auditory_selection_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN laboratory_selection_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE teaching_groups ADD COLUMN capacity INTEGER;

ALTER TABLE teaching_groups
    ADD CONSTRAINT chk_teaching_group_capacity CHECK (capacity IS NULL OR capacity > 0);
