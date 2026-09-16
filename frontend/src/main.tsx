import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import Keycloak from 'keycloak-js';
import './style.css';

const auth = new Keycloak({
  url: import.meta.env.VITE_OIDC_URL || 'http://localhost:8080', realm: import.meta.env.VITE_OIDC_REALM || 'attendance-system', clientId: import.meta.env.VITE_OIDC_CLIENT_ID || 'attendance-frontend'
});
type Identity = { email: string; role: string; studentId: string | null };

function App({ initialError }: { initialError: string }) {
  const [identity, setIdentity] = useState<Identity | null>(null);
  const [error, setError] = useState(initialError);
  const [loading, setLoading] = useState(false);
  async function loadIdentity() {
    setLoading(true);
    setError('');
    try {
      await auth.updateToken(30);
      const response = await fetch((import.meta.env.VITE_API_URL || 'http://localhost:8081') + '/api/me', {
        headers: { Authorization: 'Bearer ' + auth.token }
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
  React.useEffect(() => { if (auth.authenticated) void loadIdentity(); }, []);
  return <main>
    <p className="label">EVIDENCIJA PRISUSTVA</p>
    <h1>Dobro došli</h1>
    <p>Prijavite se institucionalnim nalogom.</p>
    {error && <p role="alert" className="error">{error}</p>}
    {auth.authenticated ? <>
      {loading && <p role="status">Provjera naloga…</p>}
      {identity && <section><h2>Vaš nalog</h2><p>{identity.email}</p>
        <p>Uloga: {identity.role === 'EMPLOYEE' ? 'Zaposleni' : 'Student'}</p>
        {identity.studentId && <p>Nalog je povezan sa studentskom evidencijom.</p>}
      </section>}
      <button disabled={loading} onClick={() => void loadIdentity()}>Provjeri nalog</button>
      <button onClick={() => void auth.logout({ redirectUri: window.location.origin + '/' })}>Odjavi se</button>
    </> : <button onClick={() => void auth.login({ redirectUri: window.location.origin + '/' })}>Prijavi se</button>}
  </main>;
}

auth.init({ onLoad: 'check-sso', pkceMethod: 'S256', checkLoginIframe: false })
  .then(() => createRoot(document.getElementById('root')!).render(<App initialError="" />))
  .catch(() => createRoot(document.getElementById('root')!).render(
    <App initialError="Servis za prijavu nije dostupan. Pokušajte ponovo." />));

