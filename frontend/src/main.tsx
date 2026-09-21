import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { GoogleLogin, GoogleOAuthProvider, googleLogout } from '@react-oauth/google';
import './style.css';
import { SessionQrPanel } from './SessionQrPanel';
import { CourseManagementPanel } from './CourseManagementPanel';
import { CourseImportPanel } from './CourseImportPanel';
import { StudentCoursesPanel } from './StudentCoursesPanel';
import { StudentAttendancePanel } from './StudentAttendancePanel';

const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8081';
const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';
type Identity = { email: string; role: string; studentId: string | null };
type LoginResponse = { token: string };
type Registration = { token: string; email: string; firstName: string | null; lastName: string | null };
type GoogleLoginResponse = LoginResponse & { registrationRequired: boolean; registration: Registration | null };

function App() {
  const [token, setToken] = useState('');
  const [identity, setIdentity] = useState<Identity | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [username, setUsername] = useState('profesor');
  const [password, setPassword] = useState('');
  const [registration, setRegistration] = useState<Registration | null>(null);
  const [indexNumber, setIndexNumber] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [courseRefresh, setCourseRefresh] = useState(0);
  const [employeeView, setEmployeeView] = useState<'courses' | 'import' | 'sessions'>('courses');
  const [importMessage, setImportMessage] = useState('');
  const [importPreviewOpen, setImportPreviewOpen] = useState(false);

  async function loadIdentity(accessToken: string) {
    setLoading(true);
    setError('');
    try {
      const response = await fetch(apiUrl + '/api/me', {
        headers: { Authorization: 'Bearer ' + accessToken }
      });
      if (!response.ok) {
        setIdentity(null);
        if (response.status === 403) {
          const problem = await response.json().catch(() => ({}));
          throw new Error(problem.detail || 'Nemate pravo pristupa.');
        }
        throw new Error(response.status === 401
          ? 'Prijava nije prihvaćena. Provjerite domen i ulogu naloga.'
          : 'Podaci trenutno nisu dostupni.');
      }
      setIdentity(await response.json());
    } catch (e) { setError(e instanceof Error ? e.message : 'Provjera prijave nije uspjela.'); }
    finally { setLoading(false); }
  }

  async function finishLogin(path: string, body: object) {
    setLoading(true);
    setError('');
    try {
      const response = await fetch(apiUrl + path, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
      });
      if (!response.ok) throw new Error('Prijava nije uspjela. Provjerite unesene podatke.');
      const result: LoginResponse = await response.json();
      setToken(result.token);
      await loadIdentity(result.token);
    } catch (e) { setError(e instanceof Error ? e.message : 'Prijava nije uspjela.'); }
    finally { setLoading(false); }
  }

  async function googleLogin(credential: string) {
    setLoading(true);
    setError('');
    try {
      const response = await fetch(apiUrl + '/api/auth/google', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ credential })
      });
      if (!response.ok) throw new Error('Google prijava nije prihvaćena. Provjerite fakultetski nalog.');
      const result: GoogleLoginResponse = await response.json();
      if (result.registrationRequired && result.registration) {
        setRegistration(result.registration);
        setFirstName(result.registration.firstName || '');
        setLastName(result.registration.lastName || '');
        return;
      }
      setToken(result.token);
      await loadIdentity(result.token);
    } catch (e) { setError(e instanceof Error ? e.message : 'Google prijava nije uspjela.'); }
    finally { setLoading(false); }
  }

  async function registerStudent(event: React.FormEvent) {
    event.preventDefault();
    if (!registration) return;
    setLoading(true);
    setError('');
    try {
      const response = await fetch(apiUrl + '/api/auth/register', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ registrationToken: registration.token, indexNumber, firstName, lastName })
      });
      if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.detail || 'Registracija nije uspjela. Provjerite unesene podatke.');
      }
      const result: LoginResponse = await response.json();
      setRegistration(null);
      setToken(result.token);
      await loadIdentity(result.token);
    } catch (e) { setError(e instanceof Error ? e.message : 'Registracija nije uspjela.'); }
    finally { setLoading(false); }
  }

  function logout() {
    googleLogout();
    setToken('');
    setIdentity(null);
    setRegistration(null);
    setError('');
    setEmployeeView('courses');
    setImportMessage('');
    setImportPreviewOpen(false);
  }

  function openEmployeeView(view: 'courses' | 'import' | 'sessions') {
    if (employeeView === 'import' && view !== 'import' && importPreviewOpen
        && !window.confirm('Učitani fajl nije sačuvan. Napustiti uvoz?')) return;
    if (view !== 'import') setImportPreviewOpen(false);
    if (view === 'import') setImportMessage('');
    setEmployeeView(view);
  }

  return <main>
    <h1>Evidencija prisustva</h1>
    {error && <p role="alert" className="error">{error}</p>}
    {token ? <>
      {loading && <p role="status">Provjera naloga…</p>}
      <div className="account-bar"><span>{identity
        ? `${identity.email} · ${identity.role === 'EMPLOYEE' ? 'Zaposleni' : 'Student'}`
        : 'Podaci o nalogu nisu učitani.'}</span>
        <button type="button" className="secondary-button" onClick={logout}>Odjavi se</button></div>
      {identity?.role === 'STUDENT' && <><StudentAttendancePanel apiUrl={apiUrl} token={token} />
        <StudentCoursesPanel apiUrl={apiUrl} token={token} /></>}
      {identity?.role === 'EMPLOYEE' && <>
        <nav className="employee-nav" aria-label="Rad sa predmetima">
          <button type="button" className={employeeView === 'courses' ? 'active' : ''}
            aria-current={employeeView === 'courses' ? 'page' : undefined}
            onClick={() => openEmployeeView('courses')}>Predmeti</button>
          <button type="button" className={employeeView === 'import' ? 'active' : ''}
            aria-current={employeeView === 'import' ? 'page' : undefined}
            onClick={() => openEmployeeView('import')}>Uvoz liste</button>
          <button type="button" className={employeeView === 'sessions' ? 'active' : ''}
            aria-current={employeeView === 'sessions' ? 'page' : undefined}
            onClick={() => openEmployeeView('sessions')}>Sesije</button>
        </nav>
        {employeeView === 'courses' && <>
          {importMessage && <p className="success" role="status">{importMessage}</p>}
          <CourseManagementPanel apiUrl={apiUrl} token={token} refreshKey={courseRefresh} />
        </>}
        {employeeView === 'import' && <CourseImportPanel apiUrl={apiUrl} token={token}
          onPreviewChange={setImportPreviewOpen} onImported={result => {
          setCourseRefresh(value => value + 1);
          setImportMessage(`Uvoz je sačuvan: ${result.enrollmentsCreated} novih upisa, ${result.groupsCreated} novih grupa. Izaberite predmet i kliknite „Učitaj predmet” za pregled.`);
          setEmployeeView('courses');
        }} />}
        {employeeView === 'sessions' && <SessionQrPanel apiUrl={apiUrl} token={token} />}
      </>}
    </> : registration ? <section>
      <h2>Završetak registracije</h2>
      <p>Unesite podatke koji nedostaju za studentsku evidenciju.</p>
      <form onSubmit={registerStudent}>
        <label>Institucionalni e-mail<input value={registration.email} readOnly /></label>
        <label>Broj indeksa<input value={indexNumber} onChange={event => setIndexNumber(event.target.value)} required maxLength={30} /></label>
        <label>Ime<input value={firstName} onChange={event => setFirstName(event.target.value)} required maxLength={100} /></label>
        <label>Prezime<input value={lastName} onChange={event => setLastName(event.target.value)} required maxLength={100} /></label>
        <button disabled={loading} type="submit">Završi registraciju</button>
        <button type="button" onClick={logout}>Odustani</button>
      </form>
    </section> : <div className="login-options">
      <section>
        <h2>Student</h2>
        <p>Prijavite se fakultetskim Google nalogom.</p>
        {googleClientId ? <GoogleLogin
          onSuccess={result => result.credential && void googleLogin(result.credential)}
          onError={() => setError('Google prijava nije uspjela.')}
        /> : <p className="notice">Google client ID nije podešen.</p>}
      </section>
      <section>
        <h2>Zaposleni</h2>
        <form onSubmit={event => { event.preventDefault(); void finishLogin('/api/auth/login', { username, password }); }}>
          <label>Korisničko ime<input value={username} onChange={event => setUsername(event.target.value)} required /></label>
          <label>Lozinka<input type="password" value={password} onChange={event => setPassword(event.target.value)} required /></label>
          <button disabled={loading} type="submit">Prijavi se</button>
        </form>
      </section>
    </div>}
  </main>;
}

createRoot(document.getElementById('root')!).render(
  <GoogleOAuthProvider clientId={googleClientId || 'not-configured'}><App /></GoogleOAuthProvider>
);

