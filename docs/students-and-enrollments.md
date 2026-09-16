# Studenti i upisi

## Model

Student sadrži broj indeksa, ime, prezime, institucionalni email i vrijeme kreiranja. Broj indeksa je tekst do 30 znakova, jedinstven u bazi. Ime i prezime imaju najviše 100 znakova. Početni i završni razmaci se uklanjaju, a višestruki razmaci u imenima svode na jedan.

Predmet (`Course`) ima jedno ili više izvođenja (`CourseOffering`). Izvođenje sadrži akademsku godinu, studijski program, tip studija, godinu studija i semestar. Akademska godina se zadaje u obliku `2026/2027`, sa dvije uzastopne godine.

Upis (`Enrollment`) povezuje studenta sa izvođenjem. Kombinacija studenta i izvođenja je jedinstvena: isti student ne može biti upisan dva puta na isto izvođenje, ali može ponovo upisati isti predmet u drugoj akademskoj godini. Ograničenje postoji i u bazi, tako da važi i za istovremene zahtjeve.

Status upisa je obavezan tekst do 30 znakova. Do definisanja zajedničkog šifrarnika API prihvata oznake poput `REDOVAN` i `OBNOVA`, ali ih ne ograničava na te dvije vrijednosti.

Grupa (`TeachingGroup`) pripada jednom izvođenju i ima oblik nastave i oznaku grupe. Dozvoljeni oblici su `LECTURE`, `AUDITORY_EXERCISE` i `LABORATORY_EXERCISE`. Isti oblik i oznaka ne mogu se ponoviti unutar istog izvođenja.

Jedan upis može imati više grupa. Sve moraju pripadati njegovom izvođenju. Upis je dozvoljen i bez grupa, kako bi raspored mogao biti napravljen naknadno.

Postojeća migracija `V1__create_course_tables.sql` već sadrži sve potrebne tabele i jedinstvena ograničenja, pa nije mijenjana.

## Email

Domen se podešava pomoću `STUDENT_EMAIL_DOMAIN`, a podrazumijevana vrijednost je `student.etf.unibl.org`. Postavka se prosljeđuje i kroz Docker Compose.

| Ime i prezime | Email |
| --- | --- |
| Đorđe Čavić | `djordje.cavic@student.etf.unibl.org` |
| Ana Marija Petrović Jovanović | `ana-marija.petrovic-jovanovic@student.etf.unibl.org` |
| Љиљана Њежић | `ljiljana.njezic@student.etf.unibl.org` |
| Drugi student Đorđe Čavić | `djordje.cavic.2@student.etf.unibl.org` |

Za email se koriste mala slova, `č` i `ć` postaju `c`, `š` postaje `s`, `ž` postaje `z`, a `đ` postaje `dj`. Srpska ćirilica se transliteriše. Razmaci između dijelova imena postaju crtice, a apostrofi se uklanjaju. Izvorno ime i prezime ostaju sačuvani sa svojim slovima.

Ako je email zauzet, dodaje se prvi slobodan nastavak `.2`, `.3` i dalje. Ovo važi i kada različiti zapisi daju isti email, npr. `Čavić` i `Cavic`. Provjera zauzetosti i jedinstveno ograničenje baze štite email; kod istovremene kolizije kreiranje se ponavlja u novoj transakciji, najviše deset pokušaja.

Dio prije znaka `@` ograničen je na 64 znaka. Kod dugih imena skraćuju se dijelovi uz očuvanje separatora između imena i prezimena i prostora za broj. Slova koja nije moguće pretvoriti podržanom transliteracijom vraćaju `400`, umjesto pravljenja neispravne adrese.

API evidentira adresu. Ne kreira stvarni poštanski sandučić ni nalog za prijavu, pa studentska lozinka nije potrebna.

## Endpointi

| Metoda | Putanja | Namjena |
| --- | --- | --- |
| POST | `/api/students` | Kreiranje studenta i emaila |
| GET | `/api/students` | Svi studenti, sortirani po prezimenu, imenu i indeksu |
| GET | `/api/students/{id}` | Detalji studenta |
| POST | `/api/courses/{courseId}/offerings` | Kreiranje izvođenja |
| GET | `/api/courses/{courseId}/offerings` | Izvođenja predmeta |
| GET | `/api/offerings/{id}` | Detalji izvođenja |
| POST | `/api/offerings/{offeringId}/groups` | Kreiranje grupe |
| GET | `/api/offerings/{offeringId}/groups` | Grupe izvođenja |
| GET | `/api/groups/{id}` | Detalji grupe |
| POST | `/api/offerings/{offeringId}/enrollments` | Upis studenta |
| GET | `/api/enrollments/{id}` | Detalji upisa |
| GET | `/api/courses/{courseId}/students` | Upisi sa studentima kroz sva izvođenja predmeta |
| GET | `/api/offerings/{offeringId}/students` | Upisi sa studentima na jednom izvođenju |
| GET | `/api/groups/{groupId}/students` | Upisi sa studentima određene grupe |
| PATCH | `/api/enrollments/{id}/status` | Promjena statusa upisa |
| PUT | `/api/enrollments/{id}/groups` | Zamjena kompletnog skupa grupa |

Pregledi studenata na predmetu i u grupi vraćaju upise sa ugniježđenim podacima studenta, identifikatorom predmeta i izvođenja, akademskom godinom, statusom, grupama i vremenom upisa. Pregled cijelog predmeta može prikazati istog studenta više puta ako postoji upis za više izvođenja. Za pregled jedne akademske godine koristi se odgovarajuće izvođenje. Liste upisa sortirane su po prezimenu, imenu i indeksu studenta.

Kreiranje vraća `201` i `Location` zaglavlje. Čitanje i izmjene vraćaju `200`. Postojeći resurs bez upisa vraća `[]`; nepostojeći student, predmet, izvođenje, grupa ili upis vraća `404`. Neispravan unos ili grupa drugog izvođenja vraća `400`. Duplikati vraćaju `409`.

## Primjer korištenja

Prvo se kreira ili pronađe predmet preko postojećeg `/api/courses` API-ja. Identifikatori ispod predstavljaju vrijednosti iz prethodnih odgovora i moraju biti stvarni UUID identifikatori.

Student, `POST /api/students`:

```json
{
  "indexNumber": "001/2026",
  "firstName": "Đorđe",
  "lastName": "Čavić"
}
```

Izvođenje, `POST /api/courses/{courseId}/offerings`:

```json
{
  "academicYear": "2026/2027",
  "studyProgram": "Računarstvo i informatika",
  "studyType": "OSNOVNE",
  "yearOfStudy": 1,
  "semester": 1
}
```

Grupa, `POST /api/offerings/{offeringId}/groups`:

```json
{
  "activityType": "LABORATORY_EXERCISE",
  "groupNumber": "1"
}
```

Upis, `POST /api/offerings/{offeringId}/enrollments`:

```json
{
  "studentId": "UUID_STUDENTA",
  "status": "REDOVAN",
  "groupIds": ["UUID_GRUPE"]
}
```

Za upis bez grupa `groupIds` može biti izostavljen ili prazan niz. Kod naknadne promjene, `PUT /api/enrollments/{id}/groups` zahtijeva `groupIds`; prazan niz uklanja sve veze sa grupama, ali zadržava upis. Slanje nepostojeće grupe ili grupe drugog izvođenja ne mijenja postojeći raspored.

Promjena statusa, `PATCH /api/enrollments/{id}/status`:

```json
{
  "status": "OBNOVA"
}
```

## Organizacija koda

Sve Java klase nalaze se u `backend/course-service/src/main/java/com/attendance/course`.

- `Student`, `CourseOffering`, `TeachingGroup` i `Enrollment` mapiraju tabele.
- Odgovarajući `Repository` interfejsi pristupaju bazi.
- `StudentService` čuva studente i rješava kolizije emaila. `StudentEmailGenerator` sadrži pravila transliteracije i formatiranja.
- `OfferingService` kreira i čita izvođenja i grupe.
- `EnrollmentService` provjerava upis i pripadnost grupa, mijenja status i grupe i čita spiskove. Promjene postojećeg upisa zaključavaju njegov red tokom transakcije.
- `StudentController`, `OfferingController` i `EnrollmentController` izlažu REST API.
- `Create...Request` i `Update...Request` predstavljaju ulazne podatke sa validacijom.
- `...Response` objekti odvajaju API odgovore od JPA entiteta.
- `ApiExceptionHandler` obrađuje validaciju, nepostojeće resurse i sukobe podataka za cijeli servis.

Autentifikacija i ograničenje pristupa po zaposlenom još nisu implementirani.

## Testovi

Iz foldera `backend/course-service`:

```powershell
.\mvnw.cmd -B -ntp test
```

`StudentEmailGeneratorTests` provjerava transliteraciju, višedijelna imena, dijakritiku, domen i dužinu emaila. `StudentEnrollmentTests` provjerava API, bazu, validaciju, duplikate, odvajanje spiskova, promjene statusa i grupa, rollback neispravnih promjena i istovremene zahtjeve. Prethodni testovi predmeta ostaju uključeni.

Podrazumijevani testovi koriste H2. Isti testovi upisa mogu se pokrenuti na praznoj, namjenskoj PostgreSQL testnoj bazi pomoću parametara `ENROLLMENT_TEST_DB_URL`, `spring.datasource.username`, `spring.datasource.password`, `spring.flyway.enabled=true` i `spring.jpa.hibernate.ddl-auto=validate`, uz `-Dtest=StudentEnrollmentTests`. Ovi testovi brišu sadržaj testnih tabela prije svakog slučaja i ne smiju se usmjeriti na razvojnu ili produkcionu bazu.
