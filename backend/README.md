# Backend

Spring Boot servisi:

- `course-service`: početni servis za predmete, izvođenja, studente, upise, grupe i uvoz.
- `attendance-service`: sesije, QR tokeni, prisustvo, izvještaji i analitika.
- `discovery-server`: Eureka registracija i otkrivanje servisa.

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

Poslovne funkcionalnosti još nisu dodane.
