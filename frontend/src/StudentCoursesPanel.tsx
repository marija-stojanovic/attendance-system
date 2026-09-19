import { useEffect, useState } from 'react';

type Props = { apiUrl: string; token: string };
type Group = { activityType: string; groupNumber: string };
type AvailableGroup = { id: string; groupNumber: string; capacity: number; occupied: number };
type GroupSelection = {
  activityType: string; selectionEnabled: boolean; currentGroupId: string | null; groups: AvailableGroup[];
};
type StudentCourse = {
  enrollmentId: string;
  offeringId: string;
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
  const [selections, setSelections] = useState<Record<string, GroupSelection[]>>({});
  const [joining, setJoining] = useState('');

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
    }).then(async loadedCourses => {
      setCourses(loadedCourses);
      const olderCourses = loadedCourses.filter(course => course.yearOfStudy > 1);
      const loadedSelections = await Promise.all(olderCourses.map(async course => {
        const response = await fetch(apiUrl + '/api/student/courses/' + course.offeringId + '/groups', {
          headers: { Authorization: 'Bearer ' + token }
        });
        if (!response.ok) return [course.offeringId, []] as const;
        return [course.offeringId, await response.json() as GroupSelection[]] as const;
      }));
      setSelections(Object.fromEntries(loadedSelections));
    })
      .catch(e => setError(e instanceof Error ? e.message : 'Predmeti nisu dostupni.'))
      .finally(() => setLoading(false));
  }, [apiUrl, token]);

  async function joinGroup(course: StudentCourse, group: AvailableGroup) {
    setJoining(group.id);
    setError('');
    try {
      const response = await fetch(apiUrl + '/api/student/courses/' + course.offeringId + '/groups/' + group.id, {
        method: 'POST', headers: { Authorization: 'Bearer ' + token }
      });
      if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.detail || 'Izbor grupe nije sačuvan.');
      }
      const updated = await response.json() as GroupSelection;
      setSelections(current => ({ ...current, [course.offeringId]: (current[course.offeringId] || [])
        .map(selection => selection.activityType === updated.activityType ? updated : selection) }));
      const selected = updated.groups.find(item => item.id === updated.currentGroupId);
      setCourses(current => current.map(item => item.offeringId === course.offeringId ? {
        ...item,
        groups: [...item.groups.filter(existing => existing.activityType !== updated.activityType),
          ...(selected ? [{ activityType: updated.activityType, groupNumber: selected.groupNumber }] : [])]
      } : item));
    } catch (e) { setError(e instanceof Error ? e.message : 'Izbor grupe nije sačuvan.'); }
    finally { setJoining(''); }
  }

  function groupNames(course: StudentCourse, activityType: string) {
    const names = course.groups.filter(group => group.activityType === activityType)
      .map(group => group.groupNumber);
    return names.length > 0 ? names.join(', ') : 'Nije određena';
  }

  function selectionPanel(course: StudentCourse) {
    const courseSelections = selections[course.offeringId] || [];
    const enabled = courseSelections.filter(selection => selection.selectionEnabled);
    if (enabled.length === 0) return null;
    return <div className="available-groups"><h4>Dostupne grupe</h4>{enabled.map(selection => {
      const title = selection.activityType === 'AUDITORY_EXERCISE' ? 'Auditorne vježbe' : 'Laboratorijske vježbe';
      const current = selection.groups.find(group => group.id === selection.currentGroupId);
      return <div key={selection.activityType} className="available-group-section">
        <strong>{title}</strong>
        <span>Moja grupa: {current?.groupNumber || 'Nije izabrana'}</span>
        <div className="available-group-buttons">{selection.groups.map(group => {
          const selected = group.id === selection.currentGroupId;
          const full = group.occupied >= group.capacity;
          return <button type="button" className={selected ? 'selected-group' : ''} key={group.id}
            disabled={joining === group.id || (full && !selected)} onClick={() => void joinGroup(course, group)}>
            <strong>{group.groupNumber}</strong><span>Popunjeno {group.occupied}/{group.capacity}</span>
          </button>;
        })}</div>
      </div>;
    })}</div>;
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
      {selectionPanel(course)}
    </article>)}</div>
  </section>;
}
