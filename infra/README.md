# Infrastruktura

Mjesto za konfiguracije servisa. Root `compose.yaml` trenutno pokreće PostgreSQL, `course-service` i Keycloak. Planirani su Dockerfile za svaku aplikaciju, Eureka i eksterna konfiguracija.

Dodati healthcheck provjere i ponašanje pri nedostupnim zavisnostima. Demo mora biti pokretljiv jednom komandom. Stvarni institucionalni kredencijali ostaju izvan Gita. Keycloak služi kao lokalna zamjena za institucionalni OIDC sistem tokom razvoja.

Keycloak administracija je dostupna na `http://localhost:8080`. Razvojno administratorsko korisničko ime i lozinka podešavaju se vrijednostima `KEYCLOAK_ADMIN_USERNAME` i `KEYCLOAK_ADMIN_PASSWORD`.

## Razvojni realm

Fajl `keycloak/attendance-system-realm.json` sadrži realm `attendance-system`, uloge `STUDENT` i `EMPLOYEE` i tri lokalna testna naloga:

| Korisničko ime | E-mail | Lozinka | Uloga |
| --- | --- | --- | --- |
| marija | marija@student.etf.org | Marija123! | STUDENT |
| dejan | dejan@student.etf.org | Dejan123! | STUDENT |
| profesor | profesor@etf.org | Profesor123! | EMPLOYEE |

Ovo su izmišljeni razvojni nalozi. E-mail adrese su označene kao potvrđene radi lokalnog testiranja, bez slanja poruka. Samostalna registracija je isključena.

Realm se učitava pri pokretanju komandom `docker compose up -d keycloak`. Ako realm već postoji u Keycloak bazi, uvoz se preskače; promjena JSON fajla neće prepisati postojeće korisnike. Podaci ostaju sačuvani u volumeu `keycloak_data`.

Za pregled otvoriti administraciju, prijaviti se administratorskim nalogom i izabrati realm `attendance-system`. U meniju `Users` nalaze se korisnici, a u `Realm roles` uloge.

Nalozi se mogu isprobati na `http://localhost:8080/realms/attendance-system/account/`, korisničkim imenom ili e-mail adresom. Za povezivanje testnih studenata sa bazom i provjeru backend pristupa pratiti ../docs/authentication.md.
