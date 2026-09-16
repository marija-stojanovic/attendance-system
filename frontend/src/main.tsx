import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { GoogleLogin, GoogleOAuthProvider, googleLogout } from '@react-oauth/google';
import './style.css';

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
  }

  return <main>
    <p className="label">EVIDENCIJA PRISUSTVA</p>
    <h1>Dobro došli</h1>
    {error && <p role="alert" className="error">{error}</p>}
    {token ? <>
      {loading && <p role="status">Provjera naloga…</p>}
      {identity && <section><h2>Vaš nalog</h2><p>{identity.email}</p>
        <p>Uloga: {identity.role === 'EMPLOYEE' ? 'Zaposleni' : 'Student'}</p>
        {identity.studentId && <p>Nalog je povezan sa studentskom evidencijom.</p>}
      </section>}
      <button disabled={loading} onClick={() => void loadIdentity(token)}>Provjeri nalog</button>
      <button onClick={logout}>Odjavi se</button>
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

