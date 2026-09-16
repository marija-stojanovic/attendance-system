# Prijava i prava pristupa

Student se prijavljuje Google nalogom sa domena `student.etf.unibl.org`. Backend provjerava Google potpis, client ID, vrijeme važenja, potvrđen e-mail i Google `hd` domen. Nakon uspješne provjere izdaje aplikacijski token sa ulogom `STUDENT`.

Za lokalno testiranje zaposlenog postoji prijava korisničkim imenom i lozinkom. Razvojni nalog `profesor` se pri prvom pokretanju dodaje u tabelu `local_users`, a lozinka se čuva kao BCrypt hash. Podrazumijevana lozinka je `Profesor123!` i može se promijeniti kroz `.env`.

Ruta `GET /api/me` dostupna je studentu i zaposlenom. Ostale `/api/**` rute dostupne su samo zaposlenom. Ako student još nije u bazi, nakon prve Google prijave unosi broj indeksa, ime i prezime. Potvrđeni Google e-mail se preuzima automatski, a registracija pravi i povezuje zapis u tabeli `students`.

Za lokalno pokretanje treba kopirati `.env.example` u `.env` i postaviti `GOOGLE_CLIENT_ID` i `APP_JWT_SECRET`. Frontend učitava Google client ID tokom Docker builda.
