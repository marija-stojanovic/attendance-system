import { useEffect, useMemo, useState } from 'react';
import { useRealtime } from './useRealtime';

type Props = { apiUrl: string; analyticsUrl: string; token: string };
type Course = { id: string; code: string; name: string };
type Offering = {
  id: string;
  academicYear: string;
  studyProgram: string;
  studyType: string;
  yearOfStudy: number;
  semester: number;
};
type Student = {
  id: string;
  firstName: string;
  lastName: string;
  indexNumber: string;
  email: string;
};
type StudentSummary = {
  student: Student;
  arrivals: number;
  absences: number;
  expectedSessions: number;
  attendancePercent: number;
};
type SessionSummary = {
  id: string;
  date: string;
  startTime: string;
  activityType: string;
  groupNumber: string;
  expectedStudents: number;
  presentStudents: number;
  attendancePercent: number;
};
type TrendPoint = {
  date: string;
  closedSessions: number;
  expectedAttendances: number;
  recordedAttendances: number;
  attendancePercent: number;
};
type GroupComparison = {
  activityType: string;
  groupNumber: string;
  closedSessions: number;
  students: number;
  expectedAttendances: number;
  recordedAttendances: number;
  attendancePercent: number;
};
type AnalyticsSummary = {
  closedSessions: number;
  lowAttendanceThresholdPercent: number;
  lowAttendanceStudents: StudentSummary[];
  sessions: SessionSummary[];
  attendanceTrend: TrendPoint[];
  groupComparisons: GroupComparison[];
  students: StudentSummary[];
};

const activityNames: Record<string, string> = {
  LECTURE: 'Predavanje',
  AUDITORY_EXERCISE: 'Auditorne vježbe',
  LABORATORY_EXERCISE: 'Laboratorijske vježbe'
};

function TrendChart({ points }: { points: TrendPoint[] }) {
  const width = 760;
  const height = 260;
  const left = 50;
  const right = 20;
  const top = 20;
  const bottom = 48;
  const graphWidth = width - left - right;
  const graphHeight = height - top - bottom;
  const x = (index: number) => points.length === 1 ? left + graphWidth / 2
    : left + index * graphWidth / (points.length - 1);
  const y = (percentage: number) => top + graphHeight - percentage * graphHeight / 100;
  const line = points.map((point, index) => `${x(index)},${y(point.attendancePercent)}`).join(' ');

  return <div className="chart-container">
    <svg className="trend-chart" viewBox={`0 0 ${width} ${height}`} role="img"
      aria-label="Linijski prikaz procenta prisustva po datumima">
      {[0, 25, 50, 75, 100].map(value => <g key={value}>
        <line x1={left} y1={y(value)} x2={width - right} y2={y(value)} className="chart-grid" />
        <text x={left - 10} y={y(value) + 4} textAnchor="end" className="chart-label">{value}%</text>
      </g>)}
      {points.length > 1 && <polyline points={line} className="chart-line" />}
      {points.map((point, index) => <g key={point.date}>
        <circle cx={x(index)} cy={y(point.attendancePercent)} r="6" className="chart-point" />
        <text x={x(index)} y={y(point.attendancePercent) - 12} textAnchor="middle" className="chart-value">
          {point.attendancePercent.toFixed(1)}%
        </text>
        <text x={x(index)} y={height - 18} textAnchor="middle" className="chart-label">
          {new Date(point.date + 'T00:00:00').toLocaleDateString('bs-BA', { day: '2-digit', month: '2-digit' })}
        </text>
      </g>)}
    </svg>
  </div>;
}

function GroupChart({ groups }: { groups: GroupComparison[] }) {
  return <div className="group-chart" role="img" aria-label="Poređenje procenta prisustva između grupa">
    {groups.map(group => <div className="group-chart-row" key={`${group.activityType}-${group.groupNumber}`}>
      <div className="group-chart-label"><strong>{group.groupNumber}</strong>
        <span>{activityNames[group.activityType] || group.activityType}</span></div>
      <div className="group-chart-track"><span style={{ width: `${group.attendancePercent}%` }} /></div>
      <strong className="group-chart-value">{group.attendancePercent.toFixed(2)}%</strong>
    </div>)}
  </div>;
}

export function AttendanceAnalyticsPanel({ apiUrl, analyticsUrl, token }: Props) {
  const [courses, setCourses] = useState<Course[]>([]);
  const [courseId, setCourseId] = useState('');
  const [offerings, setOfferings] = useState<Offering[]>([]);
  const [offeringId, setOfferingId] = useState('');
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  useRealtime(apiUrl, token, event => {
    if (event.type === 'CONNECTED'
        || (event.type !== 'QR_CHANGED' && event.offeringId === offeringId)) {
      setReload(value => value + 1);
    }
  });

  async function request<T>(baseUrl: string, path: string, signal: AbortSignal): Promise<T> {
    const response = await fetch(baseUrl + path, {
      signal,
      headers: { Authorization: 'Bearer ' + token }
    });
    if (!response.ok) {
      const problem = await response.json().catch(() => ({}));
      throw new Error(problem.detail || problem.message || 'Podaci analitike nisu dostupni.');
    }
    return response.json();
  }

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    request<Course[]>(apiUrl, '/api/courses', controller.signal).then(result => {
      setCourses(result);
      setCourseId(current => result.some(course => course.id === current) ? current : result[0]?.id || '');
    }).catch(e => {
      if (!controller.signal.aborted) setError(e instanceof Error ? e.message : 'Predmeti nisu dostupni.');
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [apiUrl, token]);

  useEffect(() => {
    setOfferings([]);
    setOfferingId('');
    setSummary(null);
    if (!courseId) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');
    request<Offering[]>(apiUrl, '/api/courses/' + courseId + '/offerings', controller.signal).then(result => {
      setOfferings(result);
      setOfferingId(result[0]?.id || '');
    }).catch(e => {
      if (!controller.signal.aborted) setError(e instanceof Error ? e.message : 'Izvođenja nisu dostupna.');
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [apiUrl, token, courseId]);

  useEffect(() => {
    setSummary(null);
    if (!offeringId) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');
    request<AnalyticsSummary>(analyticsUrl,
      '/api/analytics/offerings/' + offeringId + '/attendance-summary', controller.signal)
      .then(setSummary).catch(e => {
        if (!controller.signal.aborted) setError(e instanceof Error ? e.message : 'Analitika nije dostupna.');
      }).finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [analyticsUrl, token, offeringId, reload]);

  const averageAttendance = useMemo(() => {
    if (!summary?.students.length) return 0;
    const studentsWithSessions = summary.students.filter(student => student.expectedSessions > 0);
    if (!studentsWithSessions.length) return 0;
    return studentsWithSessions.reduce((total, student) => total + student.attendancePercent, 0)
      / studentsWithSessions.length;
  }, [summary]);

  const selectedCourse = courses.find(course => course.id === courseId);

  return <section className="analytics-panel">
    <div className="panel-heading"><div><h2>Analitika prisustva</h2>
      <p>Pregled zatvorenih sesija i posjećenosti studenata.</p></div>
      <button type="button" disabled={loading || !offeringId}
        onClick={() => setReload(value => value + 1)}>{loading ? 'Učitavanje…' : 'Osvježi'}</button>
    </div>
    {error && <p className="error" role="alert">{error}</p>}
    <div className="analytics-filters">
      <label>Predmet<select value={courseId} disabled={loading && courses.length === 0}
        onChange={event => setCourseId(event.target.value)}>
        <option value="">Izaberite predmet</option>
        {courses.map(course => <option key={course.id} value={course.id}>{course.code} — {course.name}</option>)}
      </select></label>
      <label>Akademska godina i izvođenje<select value={offeringId} disabled={!courseId || offerings.length === 0}
        onChange={event => setOfferingId(event.target.value)}>
        <option value="">Izaberite izvođenje</option>
        {offerings.map(offering => <option key={offering.id} value={offering.id}>
          {offering.academicYear} — {offering.studyProgram}, {offering.yearOfStudy}. godina, {offering.semester}. semestar
        </option>)}
      </select></label>
    </div>
    {courseId && !loading && offerings.length === 0 && <p>Predmet nema evidentirano izvođenje.</p>}
    {summary && <>
      <h3>{selectedCourse?.code} — {selectedCourse?.name}</h3>
      <div className="analytics-cards">
        <div><span>Zatvorene sesije</span><strong>{summary.closedSessions}</strong></div>
        <div><span>Upisani studenti</span><strong>{summary.students.length}</strong></div>
        <div><span>Prosječno prisustvo</span><strong>{averageAttendance.toFixed(2)}%</strong></div>
        <div><span>Ispod praga od {summary.lowAttendanceThresholdPercent.toFixed(0)}%</span>
          <strong>{summary.lowAttendanceStudents.length}</strong></div>
      </div>
      <div className="analytics-section">
        <h3>Trend prisustva tokom semestra</h3>
        {summary.attendanceTrend.length === 0 ? <p>Nema zatvorenih sesija za prikaz trenda.</p>
          : <><TrendChart points={summary.attendanceTrend} />
            <div className="table-scroll"><table>
              <thead><tr><th>Datum</th><th>Sesije</th><th>Evidentirani dolasci</th><th>Očekivani dolasci</th><th>Prisustvo</th></tr></thead>
              <tbody>{summary.attendanceTrend.map(point => <tr key={point.date}>
                <td>{new Date(point.date + 'T00:00:00').toLocaleDateString('bs-BA')}</td>
                <td>{point.closedSessions}</td><td>{point.recordedAttendances}</td>
                <td>{point.expectedAttendances}</td><td><strong>{point.attendancePercent.toFixed(2)}%</strong></td>
              </tr>)}</tbody>
            </table></div></>}
      </div>
      <div className="analytics-section">
        <h3>Poređenje grupa</h3>
        {summary.groupComparisons.length === 0 ? <p>Nema zatvorenih grupnih sesija za poređenje.</p>
          : <><GroupChart groups={summary.groupComparisons} />
            <div className="table-scroll"><table>
              <thead><tr><th>Aktivnost</th><th>Grupa</th><th>Studenti</th><th>Sesije</th><th>Dolasci</th><th>Očekivano</th><th>Prisustvo</th></tr></thead>
              <tbody>{summary.groupComparisons.map(group => <tr key={`${group.activityType}-${group.groupNumber}`}>
                <td>{activityNames[group.activityType] || group.activityType}</td><td>{group.groupNumber}</td>
                <td>{group.students}</td><td>{group.closedSessions}</td><td>{group.recordedAttendances}</td>
                <td>{group.expectedAttendances}</td><td><strong>{group.attendancePercent.toFixed(2)}%</strong></td>
              </tr>)}</tbody>
            </table></div></>}
      </div>
      <div className="analytics-section">
        <h3>Studenti ispod praga</h3>
        {summary.lowAttendanceStudents.length === 0 ? <p>Nema studenata ispod definisanog praga.</p>
          : <div className="table-scroll"><table>
            <thead><tr><th>Student</th><th>Indeks</th><th>Prisustvo</th><th>Dolasci</th><th>Očekivane sesije</th></tr></thead>
            <tbody>{summary.lowAttendanceStudents.map(item => <tr key={item.student.id}>
              <td>{item.student.lastName} {item.student.firstName}</td><td>{item.student.indexNumber}</td>
              <td><strong className="low-attendance">{item.attendancePercent.toFixed(2)}%</strong></td>
              <td>{item.arrivals}</td><td>{item.expectedSessions}</td>
            </tr>)}</tbody>
          </table></div>}
      </div>
      <div className="analytics-section">
        <h3>Prisustvo po studentu</h3>
        {summary.students.length === 0 ? <p>Za ovo izvođenje nema upisanih studenata.</p>
          : <div className="table-scroll"><table>
            <thead><tr><th>Student</th><th>Indeks</th><th>Dolasci</th><th>Izostanci</th><th>Ukupno</th><th>Prisustvo</th></tr></thead>
            <tbody>{summary.students.map(item => <tr key={item.student.id}>
              <td>{item.student.lastName} {item.student.firstName}</td><td>{item.student.indexNumber}</td>
              <td>{item.arrivals}</td><td>{item.absences}</td><td>{item.expectedSessions}</td>
              <td><strong>{item.attendancePercent.toFixed(2)}%</strong></td>
            </tr>)}</tbody>
          </table></div>}
      </div>
      <div className="analytics-section">
        <h3>Prisustvo po sesiji</h3>
        {summary.sessions.length === 0 ? <p>Nema zatvorenih sesija za izabrano izvođenje.</p>
          : <div className="table-scroll"><table>
            <thead><tr><th>Datum</th><th>Vrijeme</th><th>Aktivnost</th><th>Grupa</th><th>Prisutni</th><th>Očekivani</th><th>Prisustvo</th></tr></thead>
            <tbody>{summary.sessions.map(session => <tr key={session.id}>
              <td>{new Date(session.date + 'T00:00:00').toLocaleDateString('bs-BA')}</td>
              <td>{session.startTime.slice(0, 5)}</td><td>{activityNames[session.activityType] || session.activityType}</td>
              <td>{session.groupNumber}</td><td>{session.presentStudents}</td><td>{session.expectedStudents}</td>
              <td><strong>{session.attendancePercent.toFixed(2)}%</strong></td>
            </tr>)}</tbody>
          </table></div>}
      </div>
    </>}
  </section>;
}
