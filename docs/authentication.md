# Prijava i prava pristupa

React aplikacija na http://localhost:5173 omogućava prijavu preko Keycloaka, pregled identiteta preko GET /api/me i odjavu. Koristi Authorization Code sa PKCE S256. Tokeni se čuvaju samo u memoriji preglednika.

Backend provjerava potpis, issuer, audience course-service i vrijeme važenja tokena. Provjerava potvrđen e-mail, tačan domen i tačno jednu ulogu STUDENT ili EMPLOYEE iz realm_access.roles. Bez važeće prijave vraća 401; bez potrebnih prava vraća 403.

GET /api/me je dostupan objema ulogama. Ostale /api/** rute su dostupne samo zaposlenima. /actuator/health je javan zbog provjere rada kontejnera. Ostale rute su zabranjene.

Prvi poziv /api/me povezuje studenta po potvrđenom e-mailu i pamti issuer/sub. Naredne prijave koriste tu vezu čak i nakon promjene e-maila. Nepoznat student dobija 403, bez automatskog dodavanja. Student se ne može povezati sa drugim nalogom.

## Testni studenti

Iz PowerShella, u glavnom folderu projekta:

```powershell
Get-Content infra/keycloak/demo-students.sql | docker compose exec -T postgres psql -U course_user -d course_db
```

Skripta dodaje dva izmišljena studenta sa adresama marija@student.etf.org i dejan@student.etf.org. Ponovno izvršavanje ne pravi duplikate. Ne dodaje predmete ni upise. Redovni uvoz studenata i dalje generiše adrese po imenu i prezimenu; adresa odgovarajućeg OIDC naloga mora se poklapati pri prvom povezivanju.

## Konfiguracija

Backend: OIDC_ISSUER, OIDC_JWK_SET_URI, STUDENT_EMAIL_DOMAIN, EMPLOYEE_EMAIL_DOMAIN i FRONTEND_ORIGIN. Docker koristi interni hostname keycloak za ključeve, a localhost adresu za issuer koji preglednik dobija.

Frontend: frontend/.env.example. VITE_* vrijednosti se učitavaju pri izgradnji. Kod promjene adrese potrebno je prilagoditi i Keycloak redirectUris i webOrigins.

Realm JSON se uvozi samo kada realm još ne postoji. Izmjene postojećeg realma primjenjuju se kroz administraciju, bez brisanja baze. Klijent attendance-frontend je javni klijent, sa PKCE S256, bez implicitnog i password toka.

Odjava završava Keycloak sesiju. Već izdat pristupni token može važiti do isteka jer backend provjerava JWT lokalno. HTTP i testne lozinke namijenjeni su lokalnom razvoju.
