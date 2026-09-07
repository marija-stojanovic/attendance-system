# Predlog arhitekture

React koristi REST API dva Spring Boot servisa. `course-service` upravlja predmetima, izvođenjima, studentima, grupama i upisima. `attendance-service` upravlja sesijama, tokenima, prisustvom i izvještajima. Za provjeru upisa poziva `course-service` preko REST-a uz service discovery.

Eureka je predlog za otkrivanje servisa. Konfiguracione fajlove i varijable okruženja odvojiti od koda. Potvrditi da li predmet očekuje i Spring Cloud Config Server.

PostgreSQL lokalno može biti jedna instanca sa odvojenim bazama i korisnicima po servisu. Servisi ne pristupaju međusobnim tabelama.

## Početni model

| Entitet | Značenje | Servis |
| --- | --- | --- |
| Student | Identitet studenta i indeks | course-service |
| Course | Šifra i naziv | course-service |
| CourseOffering | Izvođenje u akademskoj godini, nosilac i metapodaci | course-service |
| Group | Grupa za oblik nastave | course-service |
| Enrollment | Upis na izvođenje i pripadnost grupama | course-service |
| Session | Termin, aktivnost, grupa, CREATED / ACTIVE / CLOSED | attendance-service |
| Attendance | Student, sesija, vrijeme, IP i user-agent | attendance-service |

## Pravila

- OIDC korisnika identifikovati provjerenim `issuer` + `subject` parom. Provjeren e-mail koristiti za početno povezivanje sa studentom. Uloga zaposlenog se ne bira na frontendu.
- Jedinstveno ograničenje sesija–student u bazi sprječava duplo prisustvo i pri istovremenim zahtjevima.
- QR token mora biti potpisan, vremenski ograničen i vezan za sesiju. Nakon rotacije odbaciti prethodni token čak i ako njegov rok još nije istekao.
- Server provjerava identitet, token, aktivnu sesiju, upis i duplikate.
- Nedostupnost servisa ne smije preskočiti provjeru upisa. Nakon izgubljenog odgovora omogućiti provjeru da li je prisustvo već upisano.
- Analitika koristi relevantne održane sesije za studenta i grupu. Definisati slučaj bez održanih sesija.
- Samo ovlašten zaposleni za predmet može uklanjati sumnjivo prisustvo.



Kompatibilnost Spring Boota i Spring Clouda: https://spring.io/projects/spring-cloud/
