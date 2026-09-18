import { FormEvent, useState } from 'react';

type Props = { apiUrl: string; token: string; onImported: () => void };
type Metadata = { academicYear: string; courseName: string; courseCode: string; studyProgram: string; studyType: string; yearOfStudy: number; semester: number; activityType: string; groupNumber: string };
type Student = { row: number; ordinalNumber: number; lastName: string; firstName: string; indexNumber: string; status: string; institutionalEmail: string; assignedGroup: string };
type Preview = { valid: boolean; metadata: Metadata | null; students: Student[]; errors: { row: number | null; field: string; message: string }[]; warnings: { row: number | null; message: string }[] };

const activityNames: Record<string, string> = {
  LECTURE: 'Predavanje', AUDITORY_EXERCISE: 'Auditorne vježbe', LABORATORY_EXERCISE: 'Laboratorijske vježbe'
};

export function CourseImportPanel({ apiUrl, token, onImported }: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  async function send(path: string) {
    if (!file) throw new Error('Izaberite Excel fajl.');
    const body = new FormData();
    body.append('file', file);
    const response = await fetch(apiUrl + path, { method: 'POST', headers: { Authorization: 'Bearer ' + token }, body });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.detail || result.message || 'Fajl nije moguće obraditi.');
    return result;
  }

  async function load(event: FormEvent) {
    event.preventDefault(); setLoading(true); setError(''); setMessage(''); setPreview(null);
    try {
      const result = await send('/api/imports/preview') as Preview;
      setPreview(result);
      if (!result.valid) setError('Fajl sadrži greške. Podaci nisu upisani.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Fajl nije moguće obraditi.'); }
    finally { setLoading(false); }
  }

  async function save() {
    setLoading(true); setError(''); setMessage('');
    try {
      await send('/api/imports');
      setMessage('Predmet i studenti su uspješno sačuvani.');
      onImported();
    } catch (e) { setError(e instanceof Error ? e.message : 'Podaci nisu sačuvani.'); }
    finally { setLoading(false); }
  }

  const metadata = preview?.metadata;
  return <section className="import-panel">
    <h2>Uvoz evidencione liste</h2>
    <p>Izaberite Excel fajl fakulteta. Prije čuvanja možete provjeriti sve učitane podatke.</p>
    <form onSubmit={load}>
      <label>Excel fajl<input type="file" accept=".xlsx" onChange={event => { setFile(event.target.files?.[0] || null); setPreview(null); setMessage(''); }} required /></label>
      <button type="submit" disabled={loading}>{loading ? 'Učitavanje…' : 'Učitaj fajl'}</button>
    </form>
    {error && <p className="error" role="alert">{error}</p>}
    {message && <p className="success">{message}</p>}
    {preview && !preview.valid && <ul>{preview.errors.map((item, index) => <li key={index}>{item.row ? `Red ${item.row}: ` : ''}{item.message}</li>)}</ul>}
    {metadata && <>
      <div className="import-summary">
        <div><strong>Predmet</strong><span>{metadata.courseName}</span></div>
        <div><strong>Šifra</strong><span>{metadata.courseCode}</span></div>
        <div><strong>Akademska godina</strong><span>{metadata.academicYear}</span></div>
        <div><strong>Studijski program</strong><span>{metadata.studyProgram}</span></div>
        <div><strong>Tip studija</strong><span>{metadata.studyType}</span></div>
        <div><strong>Godina i semestar</strong><span>{metadata.yearOfStudy}. godina, {metadata.semester}. semestar</span></div>
        <div><strong>Oblik nastave</strong><span>{activityNames[metadata.activityType] || metadata.activityType}</span></div>
        <div><strong>Grupa</strong><span>{metadata.groupNumber}</span></div>
      </div>
      <h3>Studenti ({preview.students.length})</h3>
      <div className="table-scroll"><table><thead><tr><th>R. br.</th><th>Prezime i ime</th><th>Indeks</th><th>Status</th><th>E-mail</th><th>Grupa</th></tr></thead>
        <tbody>{preview.students.map(student => <tr key={student.indexNumber}><td>{student.ordinalNumber}</td><td>{student.lastName} {student.firstName}</td><td>{student.indexNumber}</td><td>{student.status}</td><td>{student.institutionalEmail}</td><td>{student.assignedGroup}</td></tr>)}</tbody>
      </table></div>
      {preview.valid && <button disabled={loading} onClick={() => void save()}>Sačuvaj predmet i studente</button>}
    </>}
  </section>;
}
