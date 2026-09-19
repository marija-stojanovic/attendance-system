import { useEffect, useMemo, useState } from 'react';

type Props = { apiUrl: string; token: string; refreshKey?: number; importedCourseId?: string; importedOfferingId?: string };
type Course = { id: string; code: string; name: string };
type Offering = {
  id: string;
  courseId: string;
  academicYear: string;
  studyProgram: string;
  studyType: string;
  yearOfStudy: number;
  semester: number;
};
type Group = { id: string; offeringId: string; activityType: string; groupNumber: string };
type CourseEmployee = { id: string; email: string; username: string; role: string };
type Student = { id: string; indexNumber: string; firstName: string; lastName: string; institutionalEmail: string };
type Enrollment = { id: string; student: Student; status: string; groupIds: string[] };

const activityNames: Record<string, string> = {
  LECTURE: 'Predavanja',
  AUDITORY_EXERCISE: 'Auditorne grupe',
  LABORATORY_EXERCISE: 'Laboratorijske grupe'
};
const activityOrder = ['LECTURE', 'AUDITORY_EXERCISE', 'LABORATORY_EXERCISE'];

export function CourseManagementPanel({ apiUrl, token, refreshKey = 0, importedCourseId = '', importedOfferingId = '' }: Props) {
  const [courses, setCourses] = useState<Course[]>([]);
  const [selectedCourseId, setSelectedCourseId] = useState('');
  const [offerings, setOfferings] = useState<Offering[]>([]);
  const [selectedOfferingId, setSelectedOfferingId] = useState('');
  const [groups, setGroups] = useState<Group[]>([]);
  const [employees, setEmployees] = useState<CourseEmployee[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [studentSearch, setStudentSearch] = useState('');
  const [groupFilter, setGroupFilter] = useState('');
  const [error, setError] = useState('');

  const selectedCourse = courses.find(course => course.id === selectedCourseId) || null;
  const selectedOffering = offerings.find(offering => offering.id === selectedOfferingId) || null;
  const offeringsByYear = useMemo(() => offerings.reduce<Record<string, Offering[]>>((result, offering) => {
    (result[offering.academicYear] ||= []).push(offering);
    return result;
  }, {}), [offerings]);
  const groupsByActivity = useMemo(() => groups.reduce<Record<string, Group[]>>((result, group) => {
    (result[group.activityType] ||= []).push(group);
    return result;
  }, {}), [groups]);
  const groupNames = useMemo(() => new Map(groups.map(group => [group.id, group.groupNumber])), [groups]);
  const visibleEnrollments = useMemo(() => {
    const search = studentSearch.trim().toLocaleLowerCase();
    return enrollments.filter(enrollment => {
      const student = enrollment.student;
      const matchesSearch = !search || `${student.firstName} ${student.lastName} ${student.indexNumber} ${student.institutionalEmail}`
        .toLocaleLowerCase().includes(search);
      const matchesGroup = !groupFilter || (groupFilter === 'without-group'
        ? enrollment.groupIds.length === 0 : enrollment.groupIds.includes(groupFilter));
      return matchesSearch && matchesGroup;
    });
  }, [enrollments, studentSearch, groupFilter]);

  async function request<T>(path: string): Promise<T> {
    const response = await fetch(apiUrl + path, {
      headers: { Authorization: 'Bearer ' + token }
    });
    if (!response.ok) {
      const problem = await response.json().catch(() => ({}));
      throw new Error(problem.detail || problem.message || 'Zahtjev nije uspio.');
    }
    return response.json();
  }

  useEffect(() => {
    request<Course[]>('/api/courses').then(result => {
      setCourses(result);
      setSelectedCourseId(current => {
        if (importedCourseId && result.some(course => course.id === importedCourseId)) return importedCourseId;
        if (current && result.some(course => course.id === current)) return current;
        return result[0]?.id || '';
      });
    }).catch(e => setError(e instanceof Error ? e.message : 'Predmeti nisu dostupni.'));
  }, [token, refreshKey, importedCourseId]);

  useEffect(() => {
    setOfferings([]);
    setEmployees([]);
    setSelectedOfferingId('');
    setGroups([]);
    setEnrollments([]);
    if (!selectedCourseId) return;

    Promise.all([
      request<Offering[]>('/api/courses/' + selectedCourseId + '/offerings'),
      request<CourseEmployee[]>('/api/courses/' + selectedCourseId + '/employees')
    ]).then(([loadedOfferings, loadedEmployees]) => {
      setOfferings(loadedOfferings);
      setEmployees(loadedEmployees);
      const preferredOffering = importedOfferingId && loadedOfferings.some(offering => offering.id === importedOfferingId)
        ? importedOfferingId : loadedOfferings[0]?.id || '';
      setSelectedOfferingId(preferredOffering);
    }).catch(e => setError(e instanceof Error ? e.message : 'Detalji predmeta nisu dostupni.'));
  }, [selectedCourseId, refreshKey, importedOfferingId]);

  useEffect(() => {
    setGroups([]);
    setEnrollments([]);
    setStudentSearch('');
    setGroupFilter('');
    if (!selectedOfferingId) return;

    Promise.all([
      request<Group[]>('/api/offerings/' + selectedOfferingId + '/groups'),
      request<Enrollment[]>('/api/offerings/' + selectedOfferingId + '/students')
    ]).then(([loadedGroups, loadedEnrollments]) => {
      setGroups(loadedGroups);
      setEnrollments(loadedEnrollments);
    }).catch(e => setError(e instanceof Error ? e.message : 'Podaci izvođenja nisu dostupni.'));
  }, [selectedOfferingId]);

  function offeringDetails() {
    if (!selectedOffering) return null;

    return <div className="offering-details">
      <div className="offering-summary">
        <div><strong>Studijski program</strong><span>{selectedOffering.studyProgram}</span></div>
        <div><strong>Tip studija</strong><span>{selectedOffering.studyType}</span></div>
        <div><strong>Godina studija</strong><span>{selectedOffering.yearOfStudy}.</span></div>
        <div><strong>Semestar</strong><span>{selectedOffering.semester}.</span></div>
      </div>

      <div className="activity-groups">
        {activityOrder.map(activityType => <div className="activity-group" key={activityType}>
          <strong>{activityNames[activityType]}</strong>
          {(groupsByActivity[activityType] || []).length === 0 ? <span>Nema grupa</span> : <div className="group-chips">
            {(groupsByActivity[activityType] || []).map(group => <button
              className={groupFilter === group.id ? 'group-chip active' : 'group-chip'}
              key={group.id}
              type="button"
              onClick={() => setGroupFilter(current => current === group.id ? '' : group.id)}
            >{group.groupNumber}</button>)}
          </div>}
        </div>)}
      </div>

      <div className="students-panel">
        <div className="panel-heading"><h3>Studenti ({enrollments.length})</h3><span>Prikazano: {visibleEnrollments.length}</span></div>
        <div className="student-filters">
          <label>Pretraga<input value={studentSearch} onChange={event => setStudentSearch(event.target.value)} placeholder="Ime, indeks ili e-mail" /></label>
          <label>Grupa<select value={groupFilter} onChange={event => setGroupFilter(event.target.value)}>
            <option value="">Sve grupe</option>
            {activityOrder.flatMap(activityType => (groupsByActivity[activityType] || []).map(group => <option key={group.id} value={group.id}>
              {activityNames[activityType]} — {group.groupNumber}
            </option>))}
            <option value="without-group">Bez grupe</option>
          </select></label>
        </div>
        {visibleEnrollments.length === 0 ? <p>Nema studenata za izabrane kriterijume.</p> : <div className="table-scroll"><table>
          <thead><tr><th>Indeks</th><th>Prezime i ime</th><th>E-mail</th><th>Status</th><th>Grupe</th></tr></thead>
          <tbody>{visibleEnrollments.map(enrollment => <tr key={enrollment.id}>
            <td>{enrollment.student.indexNumber}</td>
            <td>{enrollment.student.lastName} {enrollment.student.firstName}</td>
            <td>{enrollment.student.institutionalEmail}</td>
            <td>{enrollment.status}</td>
            <td>{enrollment.groupIds.map(id => groupNames.get(id)).filter(Boolean).join(', ') || 'Bez grupe'}</td>
          </tr>)}</tbody>
        </table></div>}
      </div>
    </div>;
  }

  return <section className="management-panel">
    <div className="panel-heading">
      <div><p className="label">NASTAVA</p><h2>Predmeti po akademskim godinama</h2></div>
    </div>
    {error && <p role="alert" className="error">{error}</p>}
    <div className="course-layout">
      <div>
        <h3>Predmeti</h3>
        {courses.length === 0 ? <p>Nema uvezenih predmeta.</p> : <div className="item-list">
          {courses.map(course => <button
            className={course.id === selectedCourseId ? 'item active' : 'item'}
            type="button"
            key={course.id}
            onClick={() => { setError(''); setSelectedCourseId(course.id); }}
          ><strong>{course.code}</strong><span>{course.name}</span></button>)}
        </div>}
      </div>
      <div>
        {!selectedCourse ? <p>Izaberite predmet za pregled.</p> : <>
          <div className="course-title"><h3>{selectedCourse.name}</h3><p>{selectedCourse.code}</p></div>
          {employees.length > 0 && <div className="course-employees">
            <strong>Zaposleni na predmetu</strong>
            <span>{employees.map(employee => employee.email).join(', ')}</span>
          </div>}
          {Object.keys(offeringsByYear).length === 0 ? <p>Predmet nema uvezenih izvođenja.</p> : Object.entries(offeringsByYear)
            .sort(([a], [b]) => b.localeCompare(a))
            .map(([year, items]) => <section key={year} className="academic-year">
              <h4>{year}</h4>
              <div className="study-programs">{items.map(offering => <button
                className={offering.id === selectedOfferingId ? 'study-program active' : 'study-program'}
                type="button"
                key={offering.id}
                onClick={() => setSelectedOfferingId(offering.id)}
              ><strong>{offering.studyProgram}</strong><span>{offering.studyType}, {offering.yearOfStudy}. godina, {offering.semester}. semestar</span></button>)}</div>
              {items.some(offering => offering.id === selectedOfferingId) && offeringDetails()}
            </section>)}
        </>}
      </div>
    </div>
  </section>;
}
