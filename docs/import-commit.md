# Uvoz fajlova: upis u bazu

`POST /api/imports` prima Excel ili CSV fajl i upisuje validirane podatke u bazu. Endpoint ponovo čita i provjerava stvarni fajl; ne prihvata klijentsku oznaku da je pregled prošao validaciju.

## Zahtjev

Sadržaj je `multipart/form-data`:

| Parametar | Obavezan | Značenje |
| --- | --- | --- |
| `file` | Da | `.xlsx` ili UTF-8 CSV fajl |
| `academicYear` | Da | Dvije uzastopne godine, npr. `2026/2027` |
| `layout` | Ne | Raspored parsera, podrazumijevano `etf` |
| `delimiter` | Ne | CSV separator; isti kao kod pregleda |

Iz glavnog foldera projekta, uz pokrenut servis:

```powershell
curl.exe -X POST http://localhost:8081/api/imports -F "file=@backend/course-service/src/test/resources/imports/anonymous-course.csv" -F "academicYear=2026/2027"
```

Za Excel se koristi `anonymous-course.xlsx`. Ova komanda za razliku od `/api/imports/preview` stvarno upisuje podatke. Akademska godina se zadaje odvojeno jer naslov originalnog fajla navodi kalendarsku godinu, a ne nužno akademsku godinu izvođenja.

## Pravila upisa

Predmet se traži po šifri. Izvođenje se traži po predmetu, akademskoj godini, studijskom programu i tipu studija. Student se traži po indeksu, a upis po studentu i izvođenju. Ako zapis ne postoji, kreira se.

Za prvu godinu grupa se određuje za svakog studenta prema obliku nastave i indeksu. Jedan fajl zato može napraviti više grupa. Za ostale godine koristi se grupa iz zaglavlja. Student sa nepokrivenim indeksom upisuje se bez grupe i dobija upozorenje koje se vraća u odgovoru. Pravila su opisana u [dokumentu o određivanju grupa](group-assignment.md).

Novi studenti dobijaju email po postojećim pravilima i podešenom domenu. Postojećim studentima email se ne mijenja. Izvorni tekst studijskog programa ostaje sačuvan, uključujući eventualni spisak programa odvojen tačka-zarezom.

Ako upis već postoji, veza sa grupom dodaje se samo ako nedostaje. Postojeće grupe ostaju sačuvane. Ponovno slanje istog sadržaja, čak i u drugom podržanom formatu, ne pravi duplikate. Student se može ponovo upisati na izvođenje u drugoj akademskoj godini.

Sukobi vraćaju `409` i poništavaju cijeli uvoz:

- postojeća šifra predmeta sa drugačijim nazivom;
- postojeće izvođenje sa drugačijom godinom studija ili semestrom;
- postojeći indeks sa drugačijim imenom ili prezimenom;
- postojeći upis sa drugačijim statusom.

Kod poređenja imena, naziva i statusa zanemaruju se velika slova i višestruki razmaci, uz Unicode normalizaciju. Latinica i ćirilica, kao i različita slova sa dijakritikom, ne izjednačavaju se automatski pri provjeri identiteta. Sukob se rješava provjerom podataka prije novog uvoza. Uvoz ne prepisuje postojeći status ili identitet studenta.

## Transakcije i ponovljeni zahtjevi

Svi upisi jednog fajla izvršavaju se u jednoj transakciji. Greška na posljednjem redu poništava i prethodne promjene tog fajla. Raniji podaci ostaju sačuvani.

Pri radu nad postojećim predmetom i upisom koriste se zaključavanja redova. Jedinstvena ograničenja baze sprečavaju duplikate i kada dva zahtjeva istovremeno pokušaju napraviti novi zapis. Kod istovremenog sukoba cijela transakcija se ponavlja, najviše pet pokušaja; nije dozvoljeno nastaviti već poništenu transakciju.

Ponovni zahtjev se prepoznaje prema postojećim poslovnim podacima, ne prema nazivu fajla. Ne postoji evidencija istorije uploadovanih fajlova niti čuvanje originalnih dokumenata.

## Odgovor

Uspješan uvoz vraća `200`, uključujući potpuno ponovljen zahtjev:

| Polje | Značenje |
| --- | --- |
| `courseId`, `offeringId` | Identifikatori predmeta i izvođenja |
| `groupId` | Identifikator kada je korištena samo jedna grupa; kod više grupa je `null` |
| `groupIds` | Identifikatori svih grupa korištenih u uvozu |
| `courseCreated`, `offeringCreated`, `groupCreated` | Da li je napravljen predmet, izvođenje ili najmanje jedna grupa |
| `groupsCreated` | Broj novih grupa |
| `studentsCreated`, `studentsReused` | Novi i postojeći studenti |
| `enrollmentsCreated`, `enrollmentsReused` | Novi i postojeći upisi |
| `groupMembershipsAdded` | Broj dodatih veza sa grupom |
| `warnings` | Napomene za studente kojima grupa nije automatski određena |

Za drugi identičan upload broj novih studenata, upisa i veza sa grupom je nula. Neispravan fajl vraća `422` sa greškama parsera i bez upisa. Pogrešna ili izostavljena akademska godina vraća `400`. Ograničenja formata i veličine fajla ostaju ista kao kod pregleda.

## Implementacija i testovi

`ImportCommitController` prima fajl i akademsku godinu. `ImportCommitService` upravlja transakcijom, ponovnim pokušajima, pronalaženjem postojećih zapisa i kreiranjem nedostajućih podataka. `ImportResult` definiše izvještaj. Repozitoriji su dopunjeni upitima za pronalaženje jedinstvenih zapisa i zaključavanje.

`ImportCommitTests` pokriva stvarni HTTP poziv do baze, ponavljanje uvoza, više godina i predmeta, više grupa, automatsko raspoređivanje, upozorenje za nepokriveni indeks, kolizije emaila, sukobe podataka, rollback i istovremene zahtjeve. Koristi samo anonimne primjere iz prvog dijela.

Standardni testovi iz `backend/course-service`:

```powershell
.\mvnw.cmd -B -ntp test
```

Za namjensku praznu PostgreSQL testnu bazu koriste se `-Dtest=ImportCommitTests`, `-DIMPORT_TEST_DB_URL=jdbc:postgresql://localhost:55433/import_tests`, pristupni parametri `spring.datasource.username` i `spring.datasource.password`, `spring.flyway.enabled=true` i `spring.jpa.hibernate.ddl-auto=validate`. Testovi brišu testne tabele i ne smiju se usmjeravati na razvojnu ili produkcionu bazu.

Korisnički ekran za upload i autentifikacija zaposlenih nisu dio ovog backend koraka.
