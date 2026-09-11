# Backend

Spring Boot servisi:

- `course-service`: početni servis za predmete, izvođenja, studente, upise, grupe i uvoz.
- `attendance-service`: sesije, QR tokeni, prisustvo, izvještaji i analitika.
- `discovery-server`: registracija i otkrivanje servisa.

## Course service

Za pokretanje servisa i baze iz glavnog foldera projekta:

```powershell
docker compose up --build
```

Provjera servisa: `http://localhost:8081/actuator/health`

Očekivani odgovor je `{"status":"UP"}`. Flyway pri prvom pokretanju pravi početne tabele u PostgreSQL bazi.

Ako se servis pokreće direktno iz foldera `backend/course-service`, PostgreSQL prvo mora biti pokrenut:

```powershell
docker compose up -d postgres
cd backend/course-service
.\mvnw.cmd spring-boot:run
```

## Upravljanje predmetima

Osnovni predmet sadrži šifru i naziv. Podaci o akademskoj godini i studijama pripadaju izvođenju predmeta i biće obuhvaćeni zasebnom funkcionalnošću.

| Metoda | Putanja | Namjena |
| --- | --- | --- |
| POST | `/api/courses` | Dodavanje predmeta |
| GET | `/api/courses` | Svi predmeti, sortirani po šifri |
| GET | `/api/courses/{id}` | Detalji predmeta prema UUID identifikatoru |

Primjer tijela zahtjeva za dodavanje:

```json
{
  "code": "CS101",
  "name": "Programiranje"
}
```

Uspješno dodavanje vraća status `201`, zaglavlje `Location` sa putanjom predmeta i podatke `id`, `code`, `name` i `createdAt`. Pregled vraća status `200`, a prazna lista se prikazuje kao `[]`.

Šifra i naziv su obavezni. Šifra može imati najviše 50 znakova, a naziv 255. Početni i završni razmaci se uklanjaju prije validacije i čuvanja. Šifra je jedinstvena i razlikuje velika i mala slova, u skladu sa postojećim ograničenjem baze.

Neispravni podaci vraćaju `400`, nepostojeći predmet `404`, a zauzeta šifra `409`. Greške validacije sadrže polje `errors` sa porukama za šifru ili naziv.

Ova verzija vraća sve predmete. Filtriranje prema zaposlenom, autentifikacija, izmjena predmeta, izvođenja, nastavnici i grupe još nisu implementirani.

Testovi se pokreću iz foldera `backend/course-service`:

```powershell
.\mvnw.cmd test
```

Testni profil koristi H2 bazu u PostgreSQL režimu. Hibernate pravi testne tabele iz entiteta, a Flyway je isključen zbog razlika u SQL sintaksi između H2 i PostgreSQL baze. Testovi ne zahtijevaju pokrenut PostgreSQL i ne mijenjaju razvojne podatke. Ovi testovi ne provjeravaju izvršavanje produkcione migracije na PostgreSQL bazi.
