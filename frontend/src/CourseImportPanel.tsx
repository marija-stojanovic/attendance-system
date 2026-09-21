import { FormEvent, useState } from 'react';

type ImportWarning = { row: number | null; field?: string; message: string };
type ImportResult = {
  groupsCreated: number; enrollmentsCreated: number;
};
type Props = {
  apiUrl: string;
  token: string;
  onImported: (result: ImportResult) => void;
  onPreviewChange: (hasPreview: boolean) => void;
};
type Metadata = { academicYear: string; courseName: string; courseCode: string; studyProgram: string; studyType: string; yearOfStudy: number; semester: number; activityType: string; groupNumber: string };
type Student = {
  row: number; ordinalNumber: number; lastName: string; firstName: string; indexNumber: string;
  status: string; institutionalEmail: string; assignedGroup: string;
  auditoryGroup: string | null; laboratoryGroup: string | null;
};
type Preview = { valid: boolean; metadata: Metadata | null; students: Student[]; errors: { row: number | null; field: string; message: string }[]; warnings: ImportWarning[] };

const activityNames: Record<string, string> = {
  LECTURE: 'Predavanje', AUDITORY_EXERCISE: 'Auditorne vježbe', LABORATORY_EXERCISE: 'Laboratorijske vježbe'
};

export function CourseImportPanel({ apiUrl, token, onImported, onPreviewChange }: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showAllStudents, setShowAllStudents] = useState(false);

  async function send(path: string) {
    if (!file) throw new Error('Izaberite Excel ili CSV fajl.');
    const body = new FormData();
    body.append('file', file);
    const response = await fetch(apiUrl + path, { method: 'POST', headers: { Authorization: 'Bearer ' + token }, body });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.detail || result.message || 'Fajl nije moguće obraditi.');
    return result;
  }

  async function load(event: FormEvent) {
    event.preventDefault(); setLoading(true); setError(''); setPreview(null); setShowAllStudents(false); onPreviewChange(false);
    try {
      const result = await send('/api/imports/preview') as Preview;
      setPreview(result);
      onPreviewChange(true);
      if (!result.valid) setError('Fajl sadrži greške. Podaci nisu upisani.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Fajl nije moguće obraditi.');
    }
    finally { setLoading(false); }
  }

  async function save() {
    setLoading(true); setError('');
    try {
      const saved = await send('/api/imports') as ImportResult;
      setPreview(null);
      setFile(null);
      onPreviewChange(false);
      onImported(saved);
    } catch (e) { setError(e instanceof Error ? e.message : 'Podaci nisu sačuvani.'); }
    finally { setLoading(false); }
  }

  const metadata = preview?.metadata;
  const visibleStudents = showAllStudents ? preview?.students : preview?.students.slice(0, 20);
  return <section className="import-panel">
    <h2>Uvoz evidencione liste</h2>
    <p>Izaberite Excel ili CSV fajl fakulteta. Prije čuvanja možete provjeriti sve učitane podatke.</p>
    <form onSubmit={load}>
      <label>Excel ili CSV fajl<input type="file" accept=".xlsx,.csv" disabled={loading}
        onChange={event => { setFile(event.target.files?.[0] || null); setPreview(null); setError(''); setShowAllStudents(false); onPreviewChange(false); }} required /></label>
      <button type="submit" disabled={loading}>{loading ? 'Učitavanje…' : 'Učitaj fajl'}</button>
    </form>
    {error && <p className="error" role="alert">{error}</p>}
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
      <p className="notice">Ovo je pregled fajla prije čuvanja. Nakon čuvanja podatke možete otvoriti u dijelu Predmeti.</p>
      <div className="table-scroll"><table><thead><tr><th>R. br.</th><th>Prezime i ime</th><th>Indeks</th><th>Status</th><th>E-mail</th>
        {metadata.yearOfStudy === 1 ? <><th>Auditorna grupa</th><th>Laboratorijska grupa</th></> : <th>Grupa</th>}
      </tr></thead>
        <tbody>{visibleStudents?.map(student => <tr key={student.indexNumber}><td>{student.ordinalNumber}</td><td>{student.lastName} {student.firstName}</td><td>{student.indexNumber}</td><td>{student.status}</td><td>{student.institutionalEmail}</td>
          {metadata.yearOfStudy === 1 ? <><td>{student.auditoryGroup || 'Nije određena'}</td><td>{student.laboratoryGroup || 'Nije određena'}</td></> : <td>{student.assignedGroup}</td>}
        </tr>)}</tbody>
      </table></div>
      {preview.students.length > 20 && <button type="button" className="secondary-button"
        onClick={() => setShowAllStudents(value => !value)}>{showAllStudents ? 'Prikaži manje' : `Prikaži sve studente (${preview.students.length})`}</button>}
      {preview.warnings.length > 0 && <div className="warnings"><h3>Upozorenja</h3><ul>{preview.warnings.map((item, index) =>
        <li key={index}>{item.row ? `Red ${item.row}: ` : ''}{item.message}</li>)}</ul></div>}
      {preview.valid && <button disabled={loading} onClick={() => void save()}>Sačuvaj predmet i studente</button>}
    </>}
  </section>;
}
