# Backend

Spring Boot servisi:

- `course-service`: početni servis za predmete, izvođenja, studente, upise, grupe i uvoz.
- `attendance-service`: sesije, QR tokeni, prisustvo, izvještaji i analitika.
- `discovery-server`: Eureka registracija i otkrivanje servisa.

## Course service

Pokretanje iz foldera `backend/course-service`:

```powershell
.\mvnw.cmd spring-boot:run
```

Provjera servisa: `http://localhost:8081/actuator/health`

Očekivani odgovor je `{"status":"UP"}`. Poslovne funkcionalnosti i baza još nisu dodane.

