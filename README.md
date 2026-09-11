# QR Attendance System

Web sistem za digitalnu evidenciju prisustva studenata na nastavi i institucionalnim događajima.

Planirani stack: Spring Boot, React + TypeScript, PostgreSQL i Docker Compose.

## Trenutno stanje

Početna struktura i dokumentacija su postavljene. `course-service` je povezan sa PostgreSQL bazom i podržava dodavanje i pregled predmeta, uz validaciju i osnovne testove. Ostale komponente još nisu napravljene.

## Struktura

- `backend/`: planirani Spring Boot servisi.
- `frontend/`: planirana React aplikacija.
- `infra/`: buduća konfiguracija infrastrukture.
- `docs/`: arhitektura, analiza ulaza i plan razvoja.
- `test-files/`: uputstvo za lokalne testne podatke.

## Dokumentacija

- [Arhitektura](docs/architecture.md)
- [Analiza Excel šablona](docs/import-format.md)
- [Zajednički rad](CONTRIBUTING.md)

## Razvojno okruženje

Provjeriti instalacije na oba računara:

```powershell
git --version
java -version
javac -version
node --version
npm --version
docker --version
docker compose version
```

Pri generisanju aplikacija dogovoriti i zabilježiti kompatibilne verzije Jave, Spring Boota i Spring Clouda, Node.js i Docker image tagove. Uključiti Maven Wrapper i npm lock fajl u Git.

## Pokretanje

Iz glavnog foldera projekta pokrenuti:

```powershell
docker compose up --build
```

Kada se aplikacija pokrene, njeno stanje se može provjeriti na `http://localhost:8081/actuator/health`.

Za zaustavljanje koristiti:

```powershell
docker compose down
```

Vrijednosti iz `.env.example` su razvojne vrijednosti. Za lokalnu promjenu kopirati fajl kao `.env`; taj fajl se ne šalje na GitHub.

Za automatizovane testove koristiti sintetičke podatke istog rasporeda.
