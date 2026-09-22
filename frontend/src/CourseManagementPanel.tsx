import { useEffect, useMemo, useState } from 'react';
import { downloadReport } from './downloadReport';

type Props = { apiUrl: string; token: string; refreshKey?: number };
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
type Group = { id: string; offeringId: string; activityType: string; groupNumber: string; capacity: number | null };
type CourseEmployee = { id: string; email: string; username: string; role: string };
type Student = { id: string; indexNumber: string; firstName: string; lastName: string; institutionalEmail: string };
type Enrollment = { id: string; student: Student; status: string; groupIds: string[] };
type ManagedGroup = { id: string; groupNumber: string; capacity: number; occupied: number };
type GroupSettings = {
  activityType: string; studentCount: number; configured: boolean; selectionEnabled: boolean; groups: ManagedGroup[];
};
type SettingsForm = { groupCount: number | ''; groupCapacity: number | ''; selectionEnabled: boolean };

const activityNames: Record<string, string> = {
  LECTURE: 'Predavanja',
  AUDITORY_EXERCISE: 'Auditorne grupe',
  LABORATORY_EXERCISE: 'Laboratorijske grupe'
};
const activityOrder = ['LECTURE', 'AUDITORY_EXERCISE', 'LABORATORY_EXERCISE'];

export function CourseManagementPanel({ apiUrl, token, refreshKey = 0 }: Props) {
  const [courses, setCourses] = useState<Course[]>([]);
  const [coursesLoading, setCoursesLoading] = useState(true);
  const [courseToLoadId, setCourseToLoadId] = useState('');
  const [selectedCourseId, setSelectedCourseId] = useState('');
  const [offerings, setOfferings] = useState<Offering[]>([]);
  const [selectedOfferingId, setSelectedOfferingId] = useState('');
  const [groups, setGroups] = useState<Group[]>([]);
  const [employees, setEmployees] = useState<CourseEmployee[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [studentSearch, setStudentSearch] = useState('');
  const [groupFilter, setGroupFilter] = useState('');
  const [showAllStudents, setShowAllStudents] = useState(false);
  const [groupSettings, setGroupSettings] = useState<GroupSettings[]>([]);
  const [settingsForms, setSettingsForms] = useState<Record<string, SettingsForm>>({});
  const [savingSettings, setSavingSettings] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [downloading, setDownloading] = useState('');
  const [localRefresh, setLocalRefresh] = useState(0);
  const [offeringRefresh, setOfferingRefresh] = useState(0);
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
  const displayedEnrollments = showAllStudents ? visibleEnrollments : visibleEnrollments.slice(0, 25);

  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(apiUrl + path, {
      ...init,
      headers: { Authorization: 'Bearer ' + token, ...(init.body ? { 'Content-Type': 'application/json' } : {}) }
    });
    if (!response.ok) {
      const problem = await response.json().catch(() => ({}));
      throw new Error(problem.detail || problem.message || 'Zahtjev nije uspio.');
    }
    return response.status === 204 ? undefined as T : response.json();
  }

  useEffect(() => {
    let active = true;
    setCoursesLoading(true);
    request<Course[]>('/api/courses').then(result => {
      if (!active) return;
      setCourses(result);
      setCourseToLoadId(current => result.some(course => course.id === current) ? current : '');
      setSelectedCourseId(current => result.some(course => course.id === current) ? current : '');
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Predmeti nisu dostupni.'); })
      .finally(() => { if (active) setCoursesLoading(false); });
    return () => { active = false; };
  }, [token, refreshKey, localRefresh]);

  useEffect(() => {
    let active = true;
    setOfferings([]);
    setEmployees([]);
    setSelectedOfferingId('');
    setGroups([]);
    setEnrollments([]);
    setGroupSettings([]);
    setSettingsForms({});
    if (!selectedCourseId) return;

    Promise.all([
      request<Offering[]>('/api/courses/' + selectedCourseId + '/offerings'),
      request<CourseEmployee[]>('/api/courses/' + selectedCourseId + '/employees')
    ]).then(([loadedOfferings, loadedEmployees]) => {
      if (!active) return;
      setOfferings(loadedOfferings);
      setEmployees(loadedEmployees);
      setSelectedOfferingId(loadedOfferings[0]?.id || '');
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Detalji predmeta nisu dostupni.'); });
    return () => { active = false; };
  }, [selectedCourseId, refreshKey]);

  useEffect(() => {
    let active = true;
    setGroups([]);
    setEnrollments([]);
    setStudentSearch('');
    setGroupFilter('');
    setShowAllStudents(false);
    if (!selectedOfferingId) return;

    Promise.all([
      request<Group[]>('/api/offerings/' + selectedOfferingId + '/groups'),
      request<Enrollment[]>('/api/offerings/' + selectedOfferingId + '/students'),
      request<GroupSettings[]>('/api/offerings/' + selectedOfferingId + '/group-settings')
    ]).then(([loadedGroups, loadedEnrollments, loadedSettings]) => {
      if (!active) return;
      setGroups(loadedGroups);
      setEnrollments(loadedEnrollments);
      setGroupSettings(loadedSettings);
      setSettingsForms(Object.fromEntries(loadedSettings.map(setting => [setting.activityType, {
        groupCount: setting.configured ? setting.groups.length : '',
        groupCapacity: setting.configured ? Math.min(...setting.groups.map(group => group.capacity)) : '',
        selectionEnabled: setting.selectionEnabled
      }])));
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Podaci izvođenja nisu dostupni.'); });
    return () => { active = false; };
  }, [selectedOfferingId, offeringRefresh]);

  async function saveGroupSettings(activityType: string) {
    const form = settingsForms[activityType];
    const existing = groupSettings.find(setting => setting.activityType === activityType);
    if (!form) return;
    const groupCount = Number(form.groupCount);
    const groupCapacity = Number(form.groupCapacity);
    if (!Number.isInteger(groupCount) || groupCount < 1 || !Number.isInteger(groupCapacity) || groupCapacity < 1) {
      setError('Unesite broj grupa i kapacitet grupe.');
      return;
    }
    if (existing && groupCount * groupCapacity < existing.studentCount) {
      setError('Ukupan kapacitet grupa mora biti dovoljan za sve studente.');
      return;
    }
    if (existing?.configured && !window.confirm('Ponovno formiranje grupa briše postojeće izbore studenata i termine za ove grupe. Nastaviti?')) return;
    setSavingSettings(activityType);
    setError('');
    try {
      await request('/api/offerings/' + selectedOfferingId + '/group-settings/' + activityType, {
        method: 'PUT', body: JSON.stringify({ groupCount, groupCapacity, selectionEnabled: form.selectionEnabled })
      });
      setOfferingRefresh(value => value + 1);
    } catch (e) { setError(e instanceof Error ? e.message : 'Grupe nisu sačuvane.'); }
    finally { setSavingSettings(''); }
  }

  async function deleteCourse() {
    if (!selectedCourse) return;
    const confirmed = window.confirm('Brisanjem predmeta biće trajno obrisana sva njegova izvođenja, grupe, termini i upisi na ovaj predmet. Studenti ostaju sačuvani u bazi i na drugim predmetima. Nastaviti?');
    if (!confirmed) return;
    setDeleting(true);
    setError('');
    try {
      await request<void>('/api/courses/' + selectedCourse.id, { method: 'DELETE' });
      setSelectedCourseId('');
      setCourseToLoadId('');
      setLocalRefresh(value => value + 1);
    } catch (e) { setError(e instanceof Error ? e.message : 'Predmet nije obrisan.'); }
    finally { setDeleting(false); }
  }

  async function downloadSummary(format: 'xlsx' | 'pdf') {
    if (!selectedOfferingId || downloading) return;
    setDownloading(format);
    setError('');
    try {
      await downloadReport(apiUrl, token,
        `/api/offerings/${selectedOfferingId}/attendance-summary.${format}`,
        `zbirno-prisustvo-${selectedOfferingId}.${format}`);
    } catch (e) { setError(e instanceof Error ? e.message : 'Preuzimanje nije uspjelo.'); }
    finally { setDownloading(''); }
  }

  function offeringDetails() {
    if (!selectedOffering) return null;

    return <div className="offering-details">
      <div className="offering-summary">
        <div><strong>Studijski program</strong><span>{selectedOffering.studyProgram}</span></div>
        <div><strong>Tip studija</strong><span>{selectedOffering.studyType}</span></div>
        <div><strong>Godina studija</strong><span>{selectedOffering.yearOfStudy}.</span></div>
        <div><strong>Semestar</strong><span>{selectedOffering.semester}.</span></div>
      </div>

      <div className="report-downloads">
        <strong>Zbirna evidencija prisustva</strong>
        <div className="download-actions">
          <button type="button" disabled={Boolean(downloading)} onClick={() => void downloadSummary('xlsx')}>
            {downloading === 'xlsx' ? 'Preuzimanje…' : 'Preuzmi Excel'}</button>
          <button type="button" className="secondary-button" disabled={Boolean(downloading)}
            onClick={() => void downloadSummary('pdf')}>
            {downloading === 'pdf' ? 'Preuzimanje…' : 'Preuzmi PDF'}</button>
        </div>
      </div>

      <div className="activity-groups">
        {activityOrder.map(activityType => <div className="activity-group" key={activityType}>
          <strong>{activityNames[activityType]}</strong>
          {(groupsByActivity[activityType] || []).length === 0 ? <span>Nema grupa</span> : <div className="group-chips">
            {(groupsByActivity[activityType] || []).map(group => <button
              className={groupFilter === group.id ? 'group-chip active' : 'group-chip'}
              key={group.id}
              type="button"
              onClick={() => { setGroupFilter(current => current === group.id ? '' : group.id); setShowAllStudents(false); }}
            >{group.groupNumber}</button>)}
          </div>}
        </div>)}
      </div>

      {selectedOffering.yearOfStudy > 1 && <details className="group-management">
        <summary>Formiranje grupa</summary>
        <p>Unesite broj grupa i kapacitet jedne grupe za izabranu vrstu vježbi.</p>
        <div className="group-settings-grid">{groupSettings.map(setting => {
          const form = settingsForms[setting.activityType];
          if (!form) return null;
          return <div className="group-settings-card" key={setting.activityType}>
            <h4>{activityNames[setting.activityType]}</h4>
            <p>Studenata: {setting.studentCount}</p>
            <label>Broj grupa<input type="number" min="1" value={form.groupCount}
              onChange={event => setSettingsForms(current => ({ ...current, [setting.activityType]: { ...form, groupCount: event.target.value === '' ? '' : Number(event.target.value) } }))} /></label>
            <label>Broj članova u grupi<input type="number" min="1" value={form.groupCapacity}
              onChange={event => setSettingsForms(current => ({ ...current, [setting.activityType]: { ...form, groupCapacity: event.target.value === '' ? '' : Number(event.target.value) } }))} /></label>
            <label className="checkbox-label"><input type="checkbox" checked={form.selectionEnabled}
              onChange={event => setSettingsForms(current => ({ ...current, [setting.activityType]: { ...form, selectionEnabled: event.target.checked } }))} /> Omogući studentima da se učlane u grupe</label>
            {setting.groups.length > 0 && <div className="capacity-list">{setting.groups.map(group =>
              <span key={group.id}>{group.groupNumber}: {group.occupied}/{group.capacity}</span>)}</div>}
            <button type="button" disabled={savingSettings === setting.activityType}
              onClick={() => void saveGroupSettings(setting.activityType)}>Sačuvaj grupe</button>
          </div>;
        })}</div>
      </details>}

      <div className="students-panel">
        <div className="panel-heading"><h3>Studenti ({enrollments.length})</h3><span>Prikazano: {displayedEnrollments.length} od {visibleEnrollments.length}</span></div>
        <div className="student-filters">
          <label>Pretraga<input value={studentSearch} onChange={event => { setStudentSearch(event.target.value); setShowAllStudents(false); }} placeholder="Ime, indeks ili e-mail" /></label>
          <label>Grupa<select value={groupFilter} onChange={event => { setGroupFilter(event.target.value); setShowAllStudents(false); }}>
            <option value="">Sve grupe</option>
            {activityOrder.flatMap(activityType => (groupsByActivity[activityType] || []).map(group => <option key={group.id} value={group.id}>
              {activityNames[activityType]} — {group.groupNumber}
            </option>))}
            <option value="without-group">Bez grupe</option>
          </select></label>
        </div>
        {visibleEnrollments.length === 0 ? <p>Nema studenata za izabrane kriterijume.</p> : <div className="table-scroll"><table>
          <thead><tr><th>Indeks</th><th>Prezime i ime</th><th>E-mail</th><th>Status</th><th>Grupe</th></tr></thead>
          <tbody>{displayedEnrollments.map(enrollment => <tr key={enrollment.id}>
            <td>{enrollment.student.indexNumber}</td>
            <td>{enrollment.student.lastName} {enrollment.student.firstName}</td>
            <td>{enrollment.student.institutionalEmail}</td>
            <td>{enrollment.status}</td>
            <td>{enrollment.groupIds.map(id => groupNames.get(id)).filter(Boolean).join(', ') || 'Bez grupe'}</td>
          </tr>)}</tbody>
        </table></div>}
        {visibleEnrollments.length > 25 && <button type="button" className="secondary-button"
          onClick={() => setShowAllStudents(value => !value)}>{showAllStudents ? 'Prikaži manje' : `Prikaži sve (${visibleEnrollments.length})`}</button>}
      </div>
    </div>;
  }

  return <section className="management-panel">
    <div className="panel-heading">
      <div><p className="label">NASTAVA</p><h2>Predmeti po akademskim godinama</h2></div>
    </div>
    {error && <p role="alert" className="error">{error}</p>}
    {coursesLoading ? <p role="status">Učitavanje predmeta…</p> : courses.length === 0 ? <p>Nema uvezenih predmeta. Otvorite Uvoz liste da dodate predmet.</p> :
      <div className="course-picker"><label>Predmet<select value={courseToLoadId} onChange={event => {
        setCourseToLoadId(event.target.value); setSelectedCourseId(''); setError('');
      }}><option value="">Izaberite predmet</option>
        {courses.map(course => <option key={course.id} value={course.id}>{course.code} — {course.name}</option>)}
      </select></label>
      <button type="button" disabled={!courseToLoadId || courseToLoadId === selectedCourseId}
        onClick={() => setSelectedCourseId(courseToLoadId)}>Učitaj predmet</button></div>}
    <div className="course-layout">
      <div>
        {!selectedCourse ? <p>Izaberite predmet i kliknite „Učitaj predmet” za pregled.</p> : <>
          <div className="course-title"><div><h3>{selectedCourse.name}</h3><p>{selectedCourse.code}</p></div>
          </div>
          {employees.length > 0 && <details className="course-employees"><summary>Zaposleni na predmetu</summary>
            <p>{employees.map(employee => employee.email).join(', ')}</p></details>}
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
          <button className="danger-button" type="button" disabled={deleting} onClick={() => void deleteCourse()}>Obriši predmet</button>
        </>}
      </div>
    </div>
  </section>;
}
