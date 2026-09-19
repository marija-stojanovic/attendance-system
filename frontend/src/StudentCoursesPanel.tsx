import { useEffect, useState } from 'react';

type Props = { apiUrl: string; token: string };
type Group = { activityType: string; groupNumber: string };
type StudentCourse = {
  enrollmentId: string;
  courseCode: string;
  courseName: string;
  academicYear: string;
  studyProgram: string;
  studyType: string;
  yearOfStudy: number;
  semester: number;
  status: string;
  groups: Group[];
};

export function StudentCoursesPanel({ apiUrl, token }: Props) {
  const [courses, setCourses] = useState<StudentCourse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    fetch(apiUrl + '/api/student/courses', {
      headers: { Authorization: 'Bearer ' + token }
    }).then(async response => {
      if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.detail || 'Predmeti nisu dostupni.');
      }
      return response.json() as Promise<StudentCourse[]>;
    }).then(setCourses)
      .catch(e => setError(e instanceof Error ? e.message : 'Predmeti nisu dostupni.'))
      .finally(() => setLoading(false));
  }, [apiUrl, token]);

  function groupNames(course: StudentCourse, activityType: string) {
    const names = course.groups.filter(group => group.activityType === activityType)
      .map(group => group.groupNumber);
    return names.length > 0 ? names.join(', ') : 'Nije određena';
  }

  return <section className="student-courses">
    <p className="label">NASTAVA</p>
    <h2>Moji predmeti</h2>
    {loading && <p>Učitavanje predmeta…</p>}
    {error && <p className="error" role="alert">{error}</p>}
    {!loading && !error && courses.length === 0 && <p>Trenutno nemaš evidentirane predmete.</p>}
    <div className="student-course-list">{courses.map(course => <article className="student-course-card" key={course.enrollmentId}>
      <div className="student-course-title"><div><h3>{course.courseName}</h3><p>{course.courseCode}</p></div><strong>{course.academicYear}</strong></div>
      <div className="student-course-info">
        <div><strong>Studijski program</strong><span>{course.studyProgram}</span></div>
        <div><strong>Tip studija</strong><span>{course.studyType}</span></div>
        <div><strong>Godina i semestar</strong><span>{course.yearOfStudy}. godina, {course.semester}. semestar</span></div>
        <div><strong>Status</strong><span>{course.status}</span></div>
        <div><strong>Auditorna grupa</strong><span>{groupNames(course, 'AUDITORY_EXERCISE')}</span></div>
        <div><strong>Laboratorijska grupa</strong><span>{groupNames(course, 'LABORATORY_EXERCISE')}</span></div>
      </div>
    </article>)}</div>
  </section>;
}
