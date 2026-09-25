CREATE TABLE analytics_sync_queue (
    offering_id UUID PRIMARY KEY,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_analytics_sync_queue_changed_at ON analytics_sync_queue(changed_at, offering_id);

INSERT INTO analytics_sync_queue (offering_id)
SELECT id FROM course_offerings;

CREATE FUNCTION queue_analytics_update() RETURNS trigger AS $$
DECLARE
    affected_id UUID;
    related_id UUID;
BEGIN
    IF TG_TABLE_NAME = 'course_offerings' THEN
        IF TG_OP = 'DELETE' THEN affected_id := OLD.id;
        ELSE affected_id := NEW.id; END IF;
    ELSIF TG_TABLE_NAME = 'students' THEN
        INSERT INTO analytics_sync_queue (offering_id)
        SELECT course_offering_id FROM enrollments WHERE student_id = NEW.id
        ON CONFLICT (offering_id) DO UPDATE SET changed_at = CURRENT_TIMESTAMP;
        RETURN NEW;
    ELSIF TG_TABLE_NAME = 'enrollments' THEN
        IF TG_OP = 'DELETE' THEN affected_id := OLD.course_offering_id;
        ELSE affected_id := NEW.course_offering_id; END IF;
    ELSIF TG_TABLE_NAME = 'enrollment_groups' THEN
        IF TG_OP = 'DELETE' THEN related_id := OLD.enrollment_id;
        ELSE related_id := NEW.enrollment_id; END IF;
        SELECT course_offering_id INTO affected_id FROM enrollments
        WHERE id = related_id;
    ELSIF TG_TABLE_NAME = 'teaching_groups' THEN
        IF TG_OP = 'DELETE' THEN affected_id := OLD.course_offering_id;
        ELSE affected_id := NEW.course_offering_id; END IF;
    ELSIF TG_TABLE_NAME = 'teaching_sessions' THEN
        IF TG_OP = 'DELETE' THEN related_id := OLD.teaching_group_id;
        ELSE related_id := NEW.teaching_group_id; END IF;
        SELECT course_offering_id INTO affected_id FROM teaching_groups
        WHERE id = related_id;
    ELSIF TG_TABLE_NAME = 'attendances' THEN
        IF TG_OP = 'DELETE' THEN related_id := OLD.teaching_session_id;
        ELSE related_id := NEW.teaching_session_id; END IF;
        SELECT g.course_offering_id INTO affected_id FROM teaching_sessions s
        JOIN teaching_groups g ON g.id = s.teaching_group_id
        WHERE s.id = related_id;
    END IF;

    IF affected_id IS NOT NULL THEN
        INSERT INTO analytics_sync_queue (offering_id) VALUES (affected_id)
        ON CONFLICT (offering_id) DO UPDATE SET changed_at = CURRENT_TIMESTAMP;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER analytics_offerings_changed AFTER INSERT OR UPDATE OR DELETE ON course_offerings
FOR EACH ROW EXECUTE FUNCTION queue_analytics_update();
CREATE TRIGGER analytics_students_changed AFTER UPDATE OF first_name, last_name, index_number ON students
FOR EACH ROW EXECUTE FUNCTION queue_analytics_update();
CREATE TRIGGER analytics_enrollments_changed AFTER INSERT OR UPDATE OR DELETE ON enrollments
FOR EACH ROW EXECUTE FUNCTION queue_analytics_update();
CREATE TRIGGER analytics_enrollment_groups_changed AFTER INSERT OR DELETE ON enrollment_groups
FOR EACH ROW EXECUTE FUNCTION queue_analytics_update();
CREATE TRIGGER analytics_groups_changed AFTER INSERT OR UPDATE OR DELETE ON teaching_groups
FOR EACH ROW EXECUTE FUNCTION queue_analytics_update();
CREATE TRIGGER analytics_sessions_changed AFTER INSERT OR UPDATE OR DELETE ON teaching_sessions
FOR EACH ROW EXECUTE FUNCTION queue_analytics_update();
CREATE TRIGGER analytics_attendances_changed AFTER INSERT OR DELETE ON attendances
FOR EACH ROW EXECUTE FUNCTION queue_analytics_update();
