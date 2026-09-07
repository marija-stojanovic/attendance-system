# QR Attendance System

Web sistem za digitalnu evidenciju prisustva studenata na nastavi i institucionalnim događajima.

Planirani stack: Spring Boot, React + TypeScript, PostgreSQL i Docker Compose.

## Trenutno stanje

Početna struktura i dokumentacija. Aplikacije još nisu generisane i trenutno nema komande za pokretanje sistema.

## Struktura

- `backend/`: planirani Spring Boot servisi.
- `frontend/`: planirana React aplikacija.
- `infra/`: buduća konfiguracija infrastrukture.
- `docs/`: arhitektura, analiza ulaza i plan razvoja.
- `test-files/`: uputstvo za lokalne testne podatke.

## Dokumentacija

- [Arhitektura](docs/architecture.md)
- [Analiza Excel šablona](docs/import-format.md)
- [Plan razvoja](docs/backlog.md)
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

Sljedeći cilj: kostur koji se na oba računara pokreće sa `docker compose up --build`. Compose fajl će biti dodat uz aplikacije koje pokreće.

Za automatizovane testove koristiti sintetičke podatke istog rasporeda.
