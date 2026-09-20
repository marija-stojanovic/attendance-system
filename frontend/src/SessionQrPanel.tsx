import { useEffect, useState } from 'react';
import { CreateSessionForm } from './CreateSessionForm';

type Course = { id: string; code: string; name: string };
type Session = { id: string; offeringId: string; groupId: string; groupNumber: string; activityType: string; date: string; startTime: string; status: string };
type Qr = { imageDataUrl: string; pin: string; expiresAt: string; serverTime: string };
type Attendance = { id: string; studentId: string; firstName: string; lastName: string; indexNumber: string; groupNumber: string; recordedAt: string };
const activities: Record<string, string> = {
  LECTURE: 'Predavanje', AUDITORY_EXERCISE: 'Auditorne vježbe', LABORATORY_EXERCISE: 'Laboratorijske vježbe'
};
const statuses: Record<string, string> = { CREATED: 'Kreirana', ACTIVE: 'Aktivna', CLOSED: 'Zatvorena' };

async function request<T>(url: string, token: string, signal: AbortSignal, method = 'GET'): Promise<T> {
  const response = await fetch(url, { method, signal, cache: 'no-store', headers: { Authorization: 'Bearer ' + token } });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || 'Podaci nisu dostupni. Provjerite prijavu i pokušajte ponovo.');
  }
  return response.json();
}

function QrDisplay({ url, token }: { url: string; token: string }) {
  const [qr, setQr] = useState<{ image: string; pin: string; deadline: number } | null>(null);
  const [remaining, setRemaining] = useState(0);
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    let expiry: ReturnType<typeof setTimeout>;
    let busy = false;
    async function refresh() {
      if (busy || controller.signal.aborted) return;
      busy = true;
      clearTimeout(timer);
      const started = performance.now();
      try {
        const data = await request<Qr>(url, token, controller.signal);
        if (controller.signal.aborted) return;
        const lifetime = Date.parse(data.expiresAt) - Date.parse(data.serverTime) - (performance.now() - started);
        clearTimeout(expiry);
        if (lifetime > 0 && !document.hidden) {
          setQr({ image: data.imageDataUrl, pin: data.pin, deadline: performance.now() + lifetime });
          setRemaining(Math.ceil(lifetime / 1000));
          setError('');
          expiry = setTimeout(() => setQr(null), lifetime);
        } else {
          setQr(null);
        }
        timer = setTimeout(() => void refresh(), Math.max(200, lifetime));
      } catch (e) {
        if (controller.signal.aborted) return;
        setQr(null);
        setError(e instanceof Error ? e.message : 'Osvježavanje QR koda nije uspjelo.');
        timer = setTimeout(() => void refresh(), 2000);
      } finally { busy = false; }
    }
    function visibility() {
      setQr(null);
      if (!document.hidden) void refresh();
    }
    void refresh();
    document.addEventListener('visibilitychange', visibility);
    return () => {
      controller.abort();
      clearTimeout(timer);
      clearTimeout(expiry);
      document.removeEventListener('visibilitychange', visibility);
    };
  }, [url, token]);
  useEffect(() => {
    if (!qr) return;
    const timer = setInterval(() => {
      const left = qr.deadline - performance.now();
      setRemaining(Math.max(0, Math.ceil(left / 1000)));
      if (left <= 0) setQr(null);
    }, 200);
    return () => clearInterval(timer);
  }, [qr]);
  return <div className="session-qr">
    {error && <p role="alert" className="error">{error}</p>}
    {qr ? <><img src={qr.image} width="420" height="420" alt="QR kod aktivne sesije za skeniranje" />
      <p className="attendance-pin"><span>PIN kod</span><strong>{qr.pin}</strong></p>
      <button type="button" onClick={() => void navigator.clipboard.writeText(qr.pin)}>Kopiraj PIN</button>
      <p>Novi QR kod za {remaining} s</p></> : <p role="status">Čekanje važećeg QR koda…</p>}
  </div>;
}

export function SessionQrPanel({ apiUrl, token }: { apiUrl: string; token: string }) {
  const [courses, setCourses] = useState<Course[]>([]);
  const [courseId, setCourseId] = useState('');
  const [sessions, setSessions] = useState<Session[]>([]);
  const [sessionId, setSessionId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(false);
  const [attendances, setAttendances] = useState<Attendance[]>([]);
  const [attendanceReload, setAttendanceReload] = useState(0);
  const selected = sessions.find(session => session.id === sessionId);
  useEffect(() => {
    const controller = new AbortController();
    request<Course[]>(apiUrl + '/api/courses', token, controller.signal).then(setCourses)
      .catch(e => { if (!controller.signal.aborted) setError(e.message); });
    return () => controller.abort();
  }, [apiUrl, token, reload]);
  useEffect(() => {
    setSessions([]);
    setSessionId('');
    setError('');
    if (!courseId) return;
    const controller = new AbortController();
    request<Session[]>(`${apiUrl}/api/courses/${courseId}/sessions`, token, controller.signal).then(setSessions)
      .catch(e => { if (!controller.signal.aborted) setError(e.message); });
    return () => controller.abort();
  }, [apiUrl, token, courseId, reload]);
  useEffect(() => {
    if (!selected || selected.status !== 'ACTIVE') return;
    const controller = new AbortController();
    const timer = setInterval(() => {
      request<Session>(`${apiUrl}/api/sessions/${selected.id}`, token, controller.signal)
        .then(updated => setSessions(previous => previous.map(s => s.id === updated.id ? updated : s)))
        .catch(() => {});
    }, 2000);
    return () => { clearInterval(timer); controller.abort(); };
  }, [apiUrl, token, selected?.id, selected?.status]);
  useEffect(() => {
    setAttendances([]);
    if (!selected) return;
    const controller = new AbortController();
    request<Attendance[]>(`${apiUrl}/api/sessions/${selected.id}/attendances`, token, controller.signal)
      .then(setAttendances).catch(e => { if (!controller.signal.aborted) setError(e.message); });
    return () => controller.abort();
  }, [apiUrl, token, selected?.id, attendanceReload]);
  async function changeState(action: 'activate' | 'close') {
    if (!selected) return;
    setBusy(true);
    setError('');
    try {
      const updated = await request<Session>(`${apiUrl}/api/sessions/${selected.id}/${action}`, token,
        new AbortController().signal, 'POST');
      setSessions(previous => previous.map(s => s.id === updated.id ? updated : s));
    } catch (e) { setError(e instanceof Error ? e.message : 'Promjena stanja nije uspjela.'); }
    finally { setBusy(false); }
  }
  return <section className="session-panel">
    <h2>Sesije nastave</h2>
    {error && <p role="alert" className="error">{error}</p>}
    <label>Predmet<select disabled={busy || creating} value={courseId} onChange={e => {
      setSessions([]); setSessionId(''); setCourseId(e.target.value); setCreating(false); setEditing(false);
    }}><option value="">Izaberite predmet</option>
      {courses.map(course => <option key={course.id} value={course.id}>{course.code} — {course.name}</option>)}
    </select></label>
    {!courseId && <p>Izaberite predmet da biste pregledali njegove sesije ili kreirali novu.</p>}
    <button disabled={busy || creating} onClick={() => setReload(value => value + 1)}>Osvježi spisak</button>
    {courseId && !creating && <button disabled={busy} onClick={() => { setSessionId(''); setCreating(true); }}>Kreiraj novu sesiju</button>}
    {creating && <CreateSessionForm apiUrl={apiUrl} token={token} courseId={courseId}
      onCancel={() => setCreating(false)} onCreated={session => {
        setSessions(previous => [...previous, session].sort((a, b) =>
          (a.date + a.startTime).localeCompare(b.date + b.startTime)));
        setSessionId(session.id);
        setCreating(false);
        setError('');
      }} />}
    {editing && selected && <CreateSessionForm apiUrl={apiUrl} token={token} courseId={courseId} session={selected}
      onCancel={() => setEditing(false)} onCreated={session => {
        setSessions(previous => previous.map(item => item.id === session.id ? session : item)
          .sort((a, b) => (a.date + a.startTime).localeCompare(b.date + b.startTime)));
        setEditing(false); setError('');
      }} />}
    {courseId && !creating && !editing && <div>
      <h3>Pregled sesija</h3>
      {sessions.length === 0 && <p>Za ovaj predmet još nema kreiranih sesija.</p>}
      {sessions.map(session => <article key={session.id}>
        <p><strong>{session.date} u {session.startTime.slice(0, 5)}</strong><br />
          {activities[session.activityType]} — grupa {session.groupNumber}<br />
          Stanje: {statuses[session.status]}</p>
        <button disabled={busy} aria-pressed={sessionId === session.id} onClick={() => setSessionId(session.id)}>
          {sessionId === session.id ? 'Detalji su prikazani' : 'Prikaži detalje'}
        </button>
      </article>)}
    </div>}
    {selected && !creating && !editing && <>
      <h3>Detalji sesije</h3>
      <p>Predmet: {courses.find(course => course.id === courseId)?.name}<br />
        Datum: {selected.date}<br />Vrijeme početka: {selected.startTime.slice(0, 5)}<br />
        Oblik aktivnosti: {activities[selected.activityType]}<br />Grupa: {selected.groupNumber}</p>
      <p>Stanje: {statuses[selected.status]}</p>
      {selected.status === 'CREATED' && <button disabled={busy} onClick={() => setEditing(true)}>Izmijeni sesiju</button>}
      {selected.status === 'CREATED' && <button disabled={busy} onClick={() => void changeState('activate')}>Aktiviraj sesiju</button>}
      {selected.status === 'ACTIVE' && <>
        <button disabled={busy} onClick={() => void changeState('close')}>Zatvori sesiju</button>
        {!busy && <QrDisplay key={selected.id} url={`${apiUrl}/api/sessions/${selected.id}/qr`} token={token} />}
      </>}
      <div className="attendance-list">
        <div className="panel-heading"><h3>Prisutni studenti ({attendances.length})</h3>
          <button disabled={busy} onClick={() => setAttendanceReload(value => value + 1)}>Osvježi</button></div>
        {attendances.length === 0 ? <p>Prisustvo još nije evidentirano.</p> : <div className="table-scroll"><table>
          <thead><tr><th>Student</th><th>Indeks</th><th>Grupa</th><th>Vrijeme prijave</th></tr></thead>
          <tbody>{attendances.map(item => <tr key={item.id}><td>{item.lastName} {item.firstName}</td>
            <td>{item.indexNumber}</td><td>{item.groupNumber}</td><td>{new Date(item.recordedAt).toLocaleString('bs-BA')}</td></tr>)}</tbody>
        </table></div>}
      </div>
    </>}
  </section>;
}
