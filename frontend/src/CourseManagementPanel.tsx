import { FormEvent, useEffect, useMemo, useState } from 'react';

type Props = { apiUrl: string; token: string; refreshKey?: number; importedCourseId?: string; importedOfferingId?: string };
type Course = { id: string; code: string; name: string; createdAt: string };
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
type CourseEmployee = { id: string; employeeId: string; email: string; username: string; role: string };
type Student = { id: string; indexNumber: string; firstName: string; lastName: string; institutionalEmail: string };
type Enrollment = { id: string; student: Student; status: string; groupIds: string[] };

const activityNames: Record<string, string> = {
  LECTURE: 'Predavanje',
  AUDITORY_EXERCISE: 'Auditorne vježbe',
  LABORATORY_EXERCISE: 'Laboratorijske vježbe'
};

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
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [editing, setEditing] = useState(false);
  const [showCourseForm, setShowCourseForm] = useState(false);
  const [showOfferingForm, setShowOfferingForm] = useState(false);
  const [academicYear, setAcademicYear] = useState('2026/2027');
  const [studyProgram, setStudyProgram] = useState('');
  const [studyType, setStudyType] = useState('Osnovne studije');
  const [yearOfStudy, setYearOfStudy] = useState('1');
  const [semester, setSemester] = useState('1');
  const [activityType, setActivityType] = useState('LECTURE');
  const [groupNumber, setGroupNumber] = useState('');
  const [employeeEmail, setEmployeeEmail] = useState('');
  const [employeeRole, setEmployeeRole] = useState('ASSISTANT');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const selectedCourse = courses.find(course => course.id === selectedCourseId) || null;
  const selectedOffering = offerings.find(offering => offering.id === selectedOfferingId) || null;
  const offeringsByYear = useMemo(() => offerings.reduce<Record<string, Offering[]>>((result, offering) => {
    (result[offering.academicYear] ||= []).push(offering);
    return result;
  }, {}), [offerings]);
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

  async function request<T>(path: string, options?: RequestInit): Promise<T> {
    const response = await fetch(apiUrl + path, {
      ...options,
      headers: {
        Authorization: 'Bearer ' + token,
        ...(options?.body ? { 'Content-Type': 'application/json' } : {}),
        ...options?.headers
      }
    });
    if (!response.ok) {
      const problem = await response.json().catch(() => ({}));
      const validation = problem.errors ? Object.values(problem.errors).join(' ') : '';
      throw new Error(validation || problem.detail || problem.message || 'Zahtjev nije uspio.');
    }
    return response.json();
  }

  async function loadCourses(preferredCourseId = '') {
    try {
      const result = await request<Course[]>('/api/courses');
      setCourses(result);
      if (preferredCourseId && result.some(course => course.id === preferredCourseId)) {
        setSelectedCourseId(preferredCourseId);
      }
      if (selectedCourseId && !result.some(course => course.id === selectedCourseId)) setSelectedCourseId('');
    } catch (e) { setError(e instanceof Error ? e.message : 'Predmeti nisu dostupni.'); }
  }

  useEffect(() => { void loadCourses(importedCourseId); }, [token, refreshKey]);

  useEffect(() => {
    setSelectedOfferingId('');
    setGroups([]);
    setOfferings([]);
    setEmployees([]);
    setEnrollments([]);
    if (!selectedCourseId) return;
    Promise.all([
      request<Offering[]>('/api/courses/' + selectedCourseId + '/offerings'),
      request<CourseEmployee[]>('/api/courses/' + selectedCourseId + '/employees')
    ]).then(([loadedOfferings, loadedEmployees]) => {
      setOfferings(loadedOfferings);
      setEmployees(loadedEmployees);
      if (importedOfferingId && loadedOfferings.some(offering => offering.id === importedOfferingId)) {
        setSelectedOfferingId(importedOfferingId);
      }
    }).catch(e => setError(e instanceof Error ? e.message : 'Detalji predmeta nisu dostupni.'));
  }, [selectedCourseId, refreshKey]);

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
    })
      .catch(e => setError(e instanceof Error ? e.message : 'Podaci izvođenja nisu dostupni.'));
  }, [selectedOfferingId]);

  function clearNotices() {
    setError('');
    setMessage('');
  }

  function openNewCourse() {
    setCode('');
    setName('');
    setEditing(false);
    setShowCourseForm(true);
    clearNotices();
  }

  function openEditCourse() {
    if (!selectedCourse) return;
    setCode(selectedCourse.code);
    setName(selectedCourse.name);
    setEditing(true);
    setShowCourseForm(true);
    clearNotices();
  }

  async function saveCourse(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    clearNotices();
    try {
      const saved = await request<Course>(editing ? '/api/courses/' + selectedCourseId : '/api/courses', {
        method: editing ? 'PUT' : 'POST',
        body: JSON.stringify({ code, name })
      });
      await loadCourses();
      setSelectedCourseId(saved.id);
      setShowCourseForm(false);
      setMessage(editing ? 'Podaci predmeta su izmijenjeni.' : 'Predmet je dodan.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Predmet nije sačuvan.'); }
    finally { setLoading(false); }
  }

  async function saveOffering(event: FormEvent) {
    event.preventDefault();
    if (!selectedCourse) return;
    setLoading(true);
    clearNotices();
    try {
      const saved = await request<Offering>('/api/courses/' + selectedCourse.id + '/offerings', {
        method: 'POST',
        body: JSON.stringify({
          academicYear,
          studyProgram,
          studyType,
          yearOfStudy: Number(yearOfStudy),
          semester: Number(semester)
        })
      });
      setOfferings(current => [...current, saved]);
      setSelectedOfferingId(saved.id);
      setShowOfferingForm(false);
      setMessage('Akademsko izvođenje predmeta je dodano.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Izvođenje predmeta nije sačuvano.'); }
    finally { setLoading(false); }
  }

  async function saveGroup(event: FormEvent) {
    event.preventDefault();
    if (!selectedOffering) return;
    setLoading(true);
    clearNotices();
    try {
      const saved = await request<Group>('/api/offerings/' + selectedOffering.id + '/groups', {
        method: 'POST',
        body: JSON.stringify({ activityType, groupNumber })
      });
      setGroups(current => [...current, saved]);
      setGroupNumber('');
      setMessage('Oblik nastave i grupa su dodani.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Grupa nije sačuvana.'); }
    finally { setLoading(false); }
  }

  async function assignEmployee(event: FormEvent) {
    event.preventDefault();
    if (!selectedCourse) return;
    setLoading(true);
    clearNotices();
    try {
      const saved = await request<CourseEmployee>('/api/courses/' + selectedCourse.id + '/employees', {
        method: 'POST',
        body: JSON.stringify({ email: employeeEmail, role: employeeRole })
      });
      setEmployees(current => [...current, saved].sort((a, b) => a.email.localeCompare(b.email)));
      setEmployeeEmail('');
      setMessage('Zaposleni je povezan sa predmetom.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Zaposleni nije povezan sa predmetom.'); }
    finally { setLoading(false); }
  }

  return <section className="management-panel">
    <div className="panel-heading">
      <div><p className="label">NASTAVA</p><h2>Upravljanje predmetima</h2></div>
      <button type="button" onClick={openNewCourse}>Dodaj predmet</button>
    </div>
    {error && <p role="alert" className="error">{error}</p>}
    {message && <p role="status" className="success">{message}</p>}
    {showCourseForm && <form className="editor" onSubmit={saveCourse}>
      <h3>{editing ? 'Izmjena predmeta' : 'Novi predmet'}</h3>
      <label>Šifra predmeta<input value={code} onChange={event => setCode(event.target.value)} required maxLength={50} /></label>
      <label>Naziv predmeta<input value={name} onChange={event => setName(event.target.value)} required maxLength={255} /></label>
      <div><button disabled={loading} type="submit">Sačuvaj</button><button className="secondary" type="button" onClick={() => setShowCourseForm(false)}>Odustani</button></div>
    </form>}
    <div className="course-layout">
      <div>
        <h3>Predmeti</h3>
        {courses.length === 0 ? <p>Nema evidentiranih predmeta.</p> : <div className="item-list">
          {courses.map(course => <button className={course.id === selectedCourseId ? 'item active' : 'item'} type="button" key={course.id} onClick={() => { clearNotices(); setSelectedCourseId(course.id); }}>
            <strong>{course.code}</strong><span>{course.name}</span>
          </button>)}
        </div>}
      </div>
      <div>
        {!selectedCourse ? <p>Izaberite predmet za pregled detalja.</p> : <>
          <div className="detail-heading"><div><h3>{selectedCourse.name}</h3><p>{selectedCourse.code}</p></div><button className="secondary" type="button" onClick={openEditCourse}>Izmijeni</button></div>
          <div className="employees-editor">
            <h3>Nastavnici i asistenti</h3>
            <ul className="group-list">{employees.map(employee => <li key={employee.id}><span><strong>{employee.username}</strong><br />{employee.email}</span><span>{employee.role === 'TEACHER' ? 'Nastavnik' : 'Asistent'}</span></li>)}</ul>
            <form onSubmit={assignEmployee}>
              <label>E-mail zaposlenog<input type="email" value={employeeEmail} onChange={event => setEmployeeEmail(event.target.value)} required /></label>
              <label>Uloga<select value={employeeRole} onChange={event => setEmployeeRole(event.target.value)}><option value="TEACHER">Nastavnik</option><option value="ASSISTANT">Asistent</option></select></label>
              <button disabled={loading} type="submit">Poveži zaposlenog</button>
            </form>
          </div>
          <div className="panel-heading"><h3>Akademske godine</h3><button type="button" onClick={() => { setShowOfferingForm(true); clearNotices(); }}>Dodaj izvođenje</button></div>
          {showOfferingForm && <form className="editor" onSubmit={saveOffering}>
            <label>Akademska godina<input value={academicYear} onChange={event => setAcademicYear(event.target.value)} required pattern="[0-9]{4}/[0-9]{4}" placeholder="2026/2027" /></label>
            <label>Studijski program<input value={studyProgram} onChange={event => setStudyProgram(event.target.value)} required /></label>
            <label>Tip studija<input value={studyType} onChange={event => setStudyType(event.target.value)} required /></label>
            <div className="form-row"><label>Godina studija<input type="number" min="1" value={yearOfStudy} onChange={event => setYearOfStudy(event.target.value)} required /></label><label>Semestar<input type="number" min="1" value={semester} onChange={event => setSemester(event.target.value)} required /></label></div>
            <div><button disabled={loading} type="submit">Sačuvaj</button><button className="secondary" type="button" onClick={() => setShowOfferingForm(false)}>Odustani</button></div>
          </form>}
          {Object.keys(offeringsByYear).length === 0 ? <p>Predmet nema evidentirano izvođenje.</p> : Object.entries(offeringsByYear).sort(([a], [b]) => b.localeCompare(a)).map(([year, items]) => <div key={year} className="academic-year">
            <h4>{year}</h4>
            <div className="item-list">{items.map(offering => <button className={offering.id === selectedOfferingId ? 'item active' : 'item'} type="button" key={offering.id} onClick={() => setSelectedOfferingId(offering.id)}>
              <strong>{offering.studyProgram}</strong><span>{offering.studyType}, {offering.yearOfStudy}. godina, {offering.semester}. semestar</span>
            </button>)}</div>
          </div>)}
          {selectedOffering && <div className="groups-editor">
            <h3>Oblici nastave i grupe</h3>
            {groups.length === 0 ? <p>Nema evidentiranih grupa.</p> : <ul className="group-list">{groups.map(group => <li key={group.id}><strong>{activityNames[group.activityType] || group.activityType}</strong><span>Grupa {group.groupNumber}</span></li>)}</ul>}
            <form onSubmit={saveGroup}>
              <label>Oblik aktivnosti<select value={activityType} onChange={event => setActivityType(event.target.value)}><option value="LECTURE">Predavanje</option><option value="AUDITORY_EXERCISE">Auditorne vježbe</option><option value="LABORATORY_EXERCISE">Laboratorijske vježbe</option></select></label>
              <label>Broj grupe<input value={groupNumber} onChange={event => setGroupNumber(event.target.value)} required maxLength={20} /></label>
              <button disabled={loading} type="submit">Dodaj grupu</button>
            </form>
            <div className="students-panel">
              <div className="panel-heading"><h3>Studenti ({enrollments.length})</h3><span>Prikazano: {visibleEnrollments.length}</span></div>
              <div className="student-filters">
                <label>Pretraga<input value={studentSearch} onChange={event => setStudentSearch(event.target.value)} placeholder="Ime, indeks ili e-mail" /></label>
                <label>Grupa<select value={groupFilter} onChange={event => setGroupFilter(event.target.value)}>
                  <option value="">Sve grupe</option>
                  {groups.map(group => <option key={group.id} value={group.id}>{activityNames[group.activityType] || group.activityType} — {group.groupNumber}</option>)}
                  <option value="without-group">Bez grupe</option>
                </select></label>
              </div>
              {visibleEnrollments.length === 0 ? <p>Nema studenata za izabrane kriterijume.</p> : <div className="table-scroll"><table>
                <thead><tr><th>Indeks</th><th>Prezime i ime</th><th>E-mail</th><th>Status</th><th>Grupa</th></tr></thead>
                <tbody>{visibleEnrollments.map(enrollment => <tr key={enrollment.id}>
                  <td>{enrollment.student.indexNumber}</td>
                  <td>{enrollment.student.lastName} {enrollment.student.firstName}</td>
                  <td>{enrollment.student.institutionalEmail}</td>
                  <td>{enrollment.status}</td>
                  <td>{enrollment.groupIds.map(id => groupNames.get(id)).filter(Boolean).join(', ') || 'Bez grupe'}</td>
                </tr>)}</tbody>
              </table></div>}
            </div>
          </div>}
        </>}
      </div>
    </div>
  </section>;
}
