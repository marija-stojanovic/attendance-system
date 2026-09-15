# Automatsko određivanje grupa

Godina studija, oblik nastave i grupa predmeta čitaju se iz zaglavlja uvoznog fajla. Broj poslije kose crte u indeksu predstavlja godinu upisa studenta i ne koristi se kao zamjena za godinu studija iz zaglavlja.

Za predmet prve godine važe posebna pravila:

- auditorne vježbe koriste grupe `G1`–`G4`;
- laboratorijske vježbe koriste grupe `L1`–`L11`;
- predavanja koriste zajedničku grupu `ALL`.

Za ostale godine koristi se grupa iz zaglavlja fajla.

## Format indeksa

Indeks `11117/25` rastavlja se na prefiks programa `11`, redni broj `117` i godinu upisa `25`. Pravila su podešena pod `group-assignment.cohorts` u `application.yml`. Nova generacija može se dodati bez izmjene Java koda.

## Nepokriveni indeksi

Ako student prve godine nije obuhvaćen odgovarajućim rasponom, pregled uvoza ostaje ispravan, ali student nema vrijednost `assignedGroup`. U listi `warnings` vraća se broj reda i objašnjenje. Takav student se upisuje na izvođenje predmeta bez grupe, a grupa se može naknadno dodati postojećim API-jem za izmjenu upisa.

Indeks `1250/25` trenutno nije obuhvaćen nijednim laboratorijskim rasponom. Za njega se vraća poruka:

```text
Za broj indeksa 1250/25 nije određena laboratorijska grupa.
```

Upozorenje ne prekida uvoz ostalih studenata. Greške formata fajla i obaveznih podataka i dalje prekidaju cijeli uvoz.

## Rezultat uvoza

Jedan fajl prve godine može napraviti više grupa. Zbog toga odgovor sadrži `groupIds` i `groupsCreated`. Polje `groupId` ostaje popunjeno kada je korištena samo jedna grupa, a kod više grupa ima vrijednost `null`.

Ponovni uvoz ne pravi iste grupe i veze sa studentima drugi put.
