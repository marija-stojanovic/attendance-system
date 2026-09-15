# Uvoz fajlova: pregled i validacija

Prvi dio uvoza omogućava upload, čitanje i validaciju jednog `.xlsx` ili `.csv` fajla. Rezultat je pregled podataka ili lista grešaka. Ovaj endpoint ne čuva podatke u bazi niti čuva uploadovani fajl u projektnim folderima.

Drugi dio je dostupan kroz zaseban `POST /api/imports`, opisan u [uputstvu za upis uvoza](import-commit.md). Pregled i dalje ne mijenja bazu.

## Upload

`POST /api/imports/preview`, sadržaj `multipart/form-data`:

| Parametar | Značenje |
| --- | --- |
| `file` | Obavezan Excel ili CSV fajl |
| `layout` | Naziv podešenog rasporeda; podrazumijevano `etf` |
| `delimiter` | Opcioni CSV separator: zarez, tačka-zarez ili tabulator |

Primjer iz glavnog foldera projekta, uz pokrenut servis:

```powershell
curl.exe -X POST http://localhost:8081/api/imports/preview -F "file=@backend/course-service/src/test/resources/imports/anonymous-course.xlsx"
```

Za CSV zamijeniti naziv fajla sa `anonymous-course.csv`.

Ispravan fajl vraća `200`, `valid: true`, objekat `metadata`, listu `students` i praznu listu `errors`. Svaki student sadrži izvorni broj reda, prezime, ime, indeks, status i `assignedGroup`. Lista `warnings` sadrži napomene koje ne prekidaju uvoz.

Neispravni podaci vraćaju `422`, `valid: false`, `metadata: null`, praznu listu studenata i sve pronađene greške. Time se ne vraća djelimično prihvaćena lista.

```json
{
  "valid": false,
  "metadata": null,
  "students": [],
  "errors": [
    {
      "row": 10,
      "field": "firstName",
      "message": "Ime je obavezno."
    }
  ],
  "warnings": []
}
```

Za greške rasporeda koje nije moguće vezati za jedan red, `row` je `null`. U CSV-u se navodi početni fizički red zapisa, uključujući slučajeve sa višerednim poljima.

Prazan, oštećen ili nečitljiv fajl, nepoznat raspored i neispravan separator vraćaju `400`. Nepodržana ekstenzija vraća `415`, a fajl veći od 5 MB `413`.

## Raspored fajla

Jedan fajl sadrži metapodatke jednog predmeta i jednu listu studenata. Jedan raspored bira jedan Excel list; podrazumijevani naziv je `st_spisak_studenata_na_predmetu`.

Parser u prvih 100 redova traži dva odvojena zaglavlja. Pozicije kolona prepoznaje po nazivima, uz podršku latinici i srpskoj ćirilici, nezavisno od velikih slova i dijakritike u oznakama.

Metapodaci: predmet, šifra, studijski program, tip studija, godina studija, semestar, oblik nastave i grupa. Vrijednosti su podrazumijevano jedan red ispod njihovog zaglavlja.

Studenti: prezime, ime, indeks i status. Čitanje počinje ispod zaglavlja, odnosno ispod njegove posljednje spojene ćelije. Prazni redovi u studentskim kolonama se preskaču.

Spojene Excel ćelije čitaju se preko njihove početne ćelije. Indeksi i šifre čuvaju se kao tekst; za numeričke ćelije koristi se prikazana Excel vrijednost, uključujući formatirane početne nule. Ako je Excel već izgubio početne nule, parser ih ne može rekonstruisati. Formule, Excel greške i logičke vrijednosti u obaveznim poljima se odbijaju; formule se ne izvršavaju.

Naslov sa kalendarskom godinom, npr. `2025. godina`, ne pretvara se automatski u akademsku godinu. Akademska godina se zadaje pri pozivu endpointa za upis.

## Određivanje grupe

Za prvu godinu se grupa računa prema obliku nastave i broju indeksa. Auditorne vježbe koriste grupe `G1`–`G4`, laboratorijske grupe `L1`–`L11`, a predavanja zajedničku grupu `ALL`. Za ostale godine koristi se grupa iz zaglavlja fajla.

Ako indeks prve godine nije obuhvaćen podešenim rasponima, `assignedGroup` je `null`, a odgovor sadrži upozorenje sa brojem reda. Detaljna pravila su opisana u [dokumentu o određivanju grupa](group-assignment.md).

## CSV

CSV koristi UTF-8, sa ili bez BOM oznake. Podržani su zarez, tačka-zarez i tabulator. Ako separator nije zadat, prepoznaje se prema broju separatora izvan navodnika. Ako automatski izbor ne odgovara fajlu, separator se može eksplicitno poslati.

CSV ima isti logički raspored: red sa nazivima metapodataka, red sa vrijednostima, pa zaglavlje studenata i studentske redove. Broj kolona se zato može razlikovati između ovih sekcija. Polja sa separatorima ili novim redom navode se pod dvostrukim navodnicima; navodnik unutar polja zapisuje se kao `""`.

CSV sa svim metapodacima ponovljenim u svakom studentskom redu nije format ovog prvog koraka.

## Validacija

- Šifra predmeta je obavezna i ima najviše 50 znakova, a naziv najviše 255.
- Studijski program je obavezan, do 4096 znakova; više programa odvojenih tačka-zarezom ostaje jedan tekst, bez nagađanja pripadnosti studenta.
- Tip studija je obavezan, do 100 znakova, a oznaka grupe do 20.
- Godina studija i semestar moraju biti pozitivni cijeli brojevi do 32767.
- Oblici nastave se prevode na `LECTURE`, `AUDITORY_EXERCISE` ili `LABORATORY_EXERCISE`. Prepoznaju se oznake poput `предавања`, `вјежбе` i `лабораторијске вјежбе`.
- Ime, prezime i indeks koriste postojeću validaciju studenata. Provjerava se i mogućnost pravljenja emaila, ali se email ne rezerviše niti student kreira.
- Status je obavezan tekst do 30 znakova.
- Ponovljen indeks u istom fajlu je greška sa referencom na prethodni red. Provjera već postojećih podataka u bazi pripada drugom dijelu.

## Drugi fakulteti i rasporedi

Rasporedi se dodaju pod `course-import.layouts` u `application.yml`. Mogu se zadati drugi naziv lista, druga imena zaglavlja i fiksne pozicije redova i kolona.

Primjer rasporeda sa drugim oznakama, uz automatsko traženje redova:

```yaml
course-import:
  layouts:
    drugi-fakultet:
      sheet-name: Studenti
      metadata-value-offset: 1
      aliases:
        courseCode: [Oznaka predmeta]
        indexNumber: [Studentski broj]
```

Za upload se šalje `layout=drugi-fakultet`. Navedeni aliasi zamjenjuju podrazumijevane oznake samo za ta polja.

Ako zaglavlja nisu standardna, mogu se eksplicitno zadati redovi i kolone. Brojevi počinju od 1:

```yaml
course-import:
  layouts:
    fiksni:
      sheet-name: Studenti
      metadata-header-row: 4
      metadata-value-offset: 1
      student-header-row: 7
      metadata-columns:
        courseName: 8
        courseCode: 10
        studyProgram: 12
        studyType: 14
        yearOfStudy: 16
        semester: 18
        activityType: 20
        groupNumber: 22
      student-columns:
        lastName: 2
        firstName: 3
        indexNumber: 4
        status: 5
```

Fiksne kolone koriste se uz odgovarajući fiksni red zaglavlja. Ako neka kolona nije navedena, traži se po oznaci u tom redu. Za CSV se broj fiksnog reda odnosi na redni broj CSV zapisa; prijavljene greške i dalje koriste fizičke brojeve redova. Ograničenja `max-rows`, `max-columns` i `header-scan-rows` takođe su podesiva.

## Testni podaci

Anonimni primjeri se nalaze u `backend/course-service/src/test/resources/imports/anonymous-course.xlsx` i `anonymous-course.csv`. Nastali su od izmišljenih podataka, bez kopiranja originalnih studenata.

Originalni spiskovi ostaju lokalno i pokriveni su postojećim `.gitignore` pravilima. Standardni testovi ne zavise od njih. Opcioni lokalni test uključuje se parametrima `-Dlocal.import.samples=true` i `-Dlocal.import.directory=putanja-do-originala`, uz `-Dtest=ImportParserTests`. Ne kopira originalne podatke i ne ispisuje studentske spiskove.

Standardna provjera iz `backend/course-service`:

```powershell
.\mvnw.cmd -B -ntp test
```

`GroupAssignmentServiceTests` provjerava granice svih podešenih raspona i nepokriveni indeks `1250/25`.

Ovaj korak dodaje backend API. Korisnički ekran za upload i kontrola pristupa zaposlenih još nisu implementirani.
