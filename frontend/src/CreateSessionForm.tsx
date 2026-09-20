import { useEffect, useState, type FormEvent } from 'react';

type Offering = { id: string; academicYear: string; studyProgram: string; studyType: string; yearOfStudy: number; semester: number };
type Group = { id: string; activityType: string; groupNumber: string };
type Session = { id: string; offeringId: string; groupId: string; groupNumber: string; activityType: string; date: string; startTime: string; status: string };
type Props = { apiUrl: string; token: string; courseId: string; session?: Session; onCancel: () => void; onCreated: (session: Session) => void };
const activities: Record<string, string> = {
  LECTURE: 'Predavanje', AUDITORY_EXERCISE: 'Auditorne vježbe', LABORATORY_EXERCISE: 'Laboratorijske vježbe'
};

export function CreateSessionForm({ apiUrl, token, courseId, session, onCancel, onCreated }: Props) {
  const [offerings, setOfferings] = useState<Offering[]>([]);
  const [offeringId, setOfferingId] = useState(session?.offeringId || '');
  const [groups, setGroups] = useState<Group[]>([]);
  const [activity, setActivity] = useState(session?.activityType || '');
  const [groupId, setGroupId] = useState(session?.groupId || '');
  const [date, setDate] = useState(session?.date || '');
  const [time, setTime] = useState(session?.startTime.slice(0, 5) || '');
  const [loadingOfferings, setLoadingOfferings] = useState(true);
  const [loadingGroups, setLoadingGroups] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const loading = loadingOfferings || loadingGroups;
  useEffect(() => {
    const controller = new AbortController();
    fetch(apiUrl + '/api/courses/' + courseId + '/offerings', {
      signal: controller.signal, headers: { Authorization: 'Bearer ' + token }
    }).then(async response => {
      if (!response.ok) throw new Error('Učitavanje izvođenja predmeta nije uspjelo.');
      const data: Offering[] = await response.json();
      if (controller.signal.aborted) return;
      setOfferings(data);
      if (!session && data.length === 1) setOfferingId(data[0].id);
    }).catch(e => { if (!controller.signal.aborted) setError(e.message); })
      .finally(() => { if (!controller.signal.aborted) setLoadingOfferings(false); });
    return () => controller.abort();
  }, [apiUrl, token, courseId]);
  useEffect(() => {
    setGroups([]);
    if (!session || offeringId !== session.offeringId) { setActivity(''); setGroupId(''); }
    if (!offeringId) return;
    const controller = new AbortController();
    setLoadingGroups(true); setError('');
    fetch(apiUrl + '/api/offerings/' + offeringId + '/groups', {
      signal: controller.signal, headers: { Authorization: 'Bearer ' + token }
    }).then(async response => {
      if (!response.ok) throw new Error('Učitavanje grupa nije uspjelo.');
      const data: Group[] = await response.json();
      if (!controller.signal.aborted) setGroups(data);
    }).catch(e => { if (!controller.signal.aborted) setError(e.message); })
      .finally(() => { if (!controller.signal.aborted) setLoadingGroups(false); });
    return () => controller.abort();
  }, [apiUrl, token, offeringId, session]);
  async function save(event: FormEvent) {
    event.preventDefault();
    if (saving || loading || !groups.some(group => group.id === groupId && group.activityType === activity)) return;
    setSaving(true); setError('');
    try {
      const response = await fetch(session ? apiUrl + '/api/sessions/' + session.id : apiUrl + '/api/courses/' + courseId + '/sessions', {
        method: session ? 'PUT' : 'POST', headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
        body: JSON.stringify({ groupId, date, startTime: time })
      });
      if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.errors ? Object.values(problem.errors).join(' ') : problem.detail || 'Kreiranje sesije nije uspjelo.');
      }
      onCreated(await response.json());
    } catch (e) { setError(e instanceof Error ? e.message : 'Kreiranje sesije nije uspjelo.'); }
    finally { setSaving(false); }
  }
  return <form onSubmit={save}>
    <h3>{session ? 'Izmjena sesije' : 'Nova sesija'}</h3>
    {error && <p role="alert" className="error">{error}</p>}
    {loading && <p role="status">Učitavanje podataka…</p>}
    {!loading && offerings.length === 0 && <p>Predmet nema definisanu akademsku godinu i studijski program.</p>}
    <label>Izvođenje predmeta<select required disabled={saving || loading} value={offeringId} onChange={e => {
      setGroups([]); setActivity(''); setGroupId(''); setOfferingId(e.target.value);
    }}><option value="">Izaberite akademsku godinu i program</option>
      {offerings.map(offering => <option key={offering.id} value={offering.id}>
        {offering.academicYear} — {offering.studyProgram} — {offering.studyType} — godina {offering.yearOfStudy}, semestar {offering.semester}
      </option>)}
    </select></label>
    <label>Oblik aktivnosti<select required disabled={saving || loading || !offeringId} value={activity}
      onChange={e => { setActivity(e.target.value); setGroupId(''); }}>
      <option value="">Izaberite oblik aktivnosti</option>
      {Object.entries(activities).filter(([key]) => groups.some(group => group.activityType === key))
        .map(([key, label]) => <option key={key} value={key}>{label}</option>)}
    </select></label>
    {!loading && offeringId && groups.length === 0 && <p>Za izabrano izvođenje predmeta nema definisanih grupa.</p>}
    <label>Grupa<select required disabled={saving || loading || !activity} value={groupId} onChange={e => setGroupId(e.target.value)}>
      <option value="">Izaberite grupu</option>
      {groups.filter(group => group.activityType === activity).map(group =>
        <option key={group.id} value={group.id}>{group.groupNumber}</option>)}
    </select></label>
    <label>Datum<input type="date" required disabled={saving} value={date} onChange={e => setDate(e.target.value)} /></label>
    <label>Vrijeme početka<input type="time" required disabled={saving} value={time} onChange={e => setTime(e.target.value)} /></label>
    <div><button type="submit" disabled={saving || loading || !groupId || !date || !time}>
      {saving ? 'Čuvanje…' : session ? 'Sačuvaj izmjene' : 'Sačuvaj sesiju'}
    </button><button type="button" disabled={saving} onClick={onCancel}>Odustani</button></div>
  </form>;
}
