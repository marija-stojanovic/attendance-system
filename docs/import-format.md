# Analiza Excel šablona

Svih pet fajlova ima list `st_spisak_studenata_na_predmetu` sa 30 kolona. Broj zapisa ispod predstavlja redove sa indeksom oblika cifre/cifre, a ne jedinstvene studente kroz sve predmete.

| Šifra | Predmet | Oblik | Godina studija | Semestar | Redovi | Zapisi |
| --- | --- | --- | --- | --- | --- | --- |
| 2228 | Osnovi računarske tehnike | vježbe | 1 | 1 | 10–242 | 233 |
| 2235 | Programiranje 2 | vježbe | 1 | 2 | 10–192 | 183 |
| 2277 | Interakcija čovjek - računar | laboratorijske vježbe | 4 | 7 | 8–92 | 85 |
| 2278 | Informacioni sistemi | laboratorijske vježbe | 4 | 7 | 8–102 | 95 |
| 2296 | Projektovanje informacionih sistema u internet okruženju | laboratorijske vježbe | 4 | 8 | 8–67 | 60 |

Nazivi su ovdje transliterisani; izvori koriste ćirilicu. Svi fajlovi navode 2025. godinu u A2 i grupu 1.

## Metapodaci

Nazivi su u redu 4, vrijednosti u redu 5:

| Ćelija | Značenje |
| --- | --- |
| H5 | Predmet |
| J5 | Šifra |
| L5 | Studijski program; može sadržati više programa odvojenih tačka-zarezom |
| N5 | Tip studija |
| P5 | Godina studija |
| R5 | Semestar |
| T5 | Oblik nastave |
| V5 | Grupa |

## Studentska tabela

Red 7 označava A: redni broj, B: prezime, C: ime, D: indeks, E: status. Ostale kolone služe evidenciji časova. Podaci počinju u redu 10 za vježbe, odnosno 8 za laboratorije.

## Pravila za budući parser

- Odvojeno čitati metapodatke i studentsku tabelu. Prvi red nije zaglavlje studenata.
- Mapiranje oznaka i pozicija držati konfigurabilnim za druge šablone.
- Obraditi ćirilicu, spojene ćelije i prazne redove.
- Indeks i šifru čuvati kao tekst. Raspone indeksa porediti po potvrđenim komponentama, ne leksikografski.
- Postoje višedijelna imena. Potvrditi transliteraciju, razmake, dijakritike i kolizije pri formiranju institucionalnog e-maila.
- Razlikovati godinu upisa, godinu studija i akademsku godinu izvođenja. A2 ne dokazuje da je akademska godina 2025/26.
- Uskladiti grupu iz fajla sa posebnim pravilima grupa iz zadatka.
- Validirati prije upisa, prijaviti broj problematičnog reda i spriječiti dupliranje pri ponovnom uvozu.
- CSV šablon, kodiranje i separator tek treba potvrditi jer CSV nije dostavljen.

Parser još nije implementiran.
