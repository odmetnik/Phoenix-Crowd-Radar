# Phoenix Crowd Radar — 0.2.0-dev

Native Android cyber radar za pronalaženje kolega u gužvi. Phoenix Art / by Željko Kušter.

**Izvorni razvojni projekt. Android build i fizički test još nisu izvršeni; ovo nije provjeren instalacijski APK.**

## Dodano u 0.2

- Promjenjiv naziv grupe/događaja. „Interliber” nije ugrađen kao obvezan naziv.
- Korisničko ime koje svatko postavlja u svom profilu; model telefona očitava se automatski.
- Stvarno generiranje i skeniranje QR pozivnice kroz ZXing.
- Dodavanje člana lokalnom Nearby vezom: vlasnik odobrava zahtjev i pokazuje kod; drugi korisnik upisuje taj kod. Ne koristi se telefonski broj ni račun na serveru.
- Google Nearby provjera koda na oba kraja prije prijenosa grupne pozivnice. Grupni ključ nije izveden iz kratkog PIN-a. Otvaranje za dodavanje traje najviše 90 s.
- Šifrirana GATT razmjena imena, modela, stabilnog lokalnog ID-a i podržanog hardvera između uređaja iste grupe.
- Kartica člana pokazuje zajedničku podršku obaju telefona, aktivni BLE način i činjenicu da preciznost u metrima još nije izmjerena.
- Naziv grupe prenosi se pozivnicom i naknadnom razmjenom profila s većom revizijom. Izmjena profila/naziva može trebati do sljedećeg očitanja (oko 30 s).

## Status

| Funkcija | Stanje |
|---|---|
| Cyber dashboard, kružni radar, demo | Implementirano u izvoru |
| Grupni BLE presence, RSSI signalni razredi | Implementirano u izvoru; test na telefonima obvezan |
| QR i brojčano uparivanje | Implementirano u izvoru; kamera/radio/Google Play usluge nisu testirani |
| Imena, modeli i hardverske mogućnosti članova | AES-GCM GATT razmjena implementirana u izvoru; runtime nije potvrđen |
| Kompas | Orijentacija vlastitog telefona, ne smjer člana |
| UWB / Wi-Fi Aware / RTT | Detekcija hardverske podrške i usporedba dvaju profila; mjerenje nije integrirano |
| GPS | Geometrija i konzervativna pravila preciznosti u testiranoj jezgri; koordinate se još ne prikupljaju i ne razmjenjuju |
| PING, mesh i rad u pozadini | Nisu implementirani |

## Tijek

1. **Moj profil i mogućnosti** → upiši ime.
2. **Grupa → Promijeni naziv grupe** → primjerice „Ekipa”, „Sajam”, „Planinarenje”.
3. **Kreiraj novu grupu** na jednom telefonu.
4. Dodavanje QR-om: prvi pokaže QR pozivnicu, drugi odabere **Skeniraj QR kod**.
5. Dodavanje brojem: prvi odabere **Dodaj člana brojem**; drugi **Pridruži se upisom broja**, izabere kolegin uređaj i upiše broj s njegova ekrana. Prvi izričito odobrava člana.
6. Oba pokrenu radar. Kada se razmijene profili, kartice prikazuju imena i modele umjesto privremenog session ID-a.

Za brojčano uparivanje moraju biti uključeni Bluetooth i Wi-Fi. Ne treba internetska veza niti spajanje na router; trebaju kompatibilne Google Play usluge. QR i osnovni BLE/GATT nemaju taj zahtjev. Nearby SDK ima vlastitu analitiku prema Googleovoj dokumentaciji; ne opisivati cijelu aplikaciju kao aplikaciju bez telemetrije prije zasebne provjere SDK-a.

QR pozivnica sadrži grupni ključ i daje pristup grupi. Pozivnicu pokazivati samo članovima. Svi članovi koji znaju ključ mogu pozivati druge. Uklanjanje pojedinca još nema zaseban protokol; preostali stvaraju novu grupu.

## Što znači prikaz preciznosti

„UWB podržan” nije „UWB aktivan”. „Zajednički UWB hardver” nije jamstvo API interoperabilnosti ili dostupnog kuta. Stvarna aktivna preciznost dolazi tek iz konkretnih mjerenja. Trenutačni BLE radar ne prikazuje izmišljene metre ni kutove. Imena i modeli prijavljeni su profilima članova, nisu neovisno potvrđen identitet ili hardverska atestacija.

Aplikacija 0.2 radi samo dok je otvorena. Prebacivanje u drugi app/zaključavanje zaustavlja rad. Demo koristi jasno označene simulirane osobe.

## Build i provjera

JDK 17, Gradle 8.11.1, Android SDK 35. Gradle wrapper nije uključen. Za puni Android build treba preuzeti Android/Google Maven ovisnosti.

```bash
bash tests/run.sh
gradle :app:assembleDebug :app:lintDebug
```

CI workflow izrađuje razvojni APK kao artifact kada se projekt postavi u GitHub repozitorij. Repozitorij: https://github.com/odmetnik/Phoenix-Crowd-Radar . Stanje konkretnog builda vidi se u kartici Actions. Za stalne instalacije treba konfigurirati trajni potpisni ključ; prolazni CI debug ključevi ne jamče kompatibilne nadogradnje.

Lokalno: **24 provjere jezgre prolaze**, svih 10 Java datoteka uspješno parsirano. To nije kompilacija Android aplikacije ni dokaz rada BLE-a/QR-a/Nearbyja. Detalji: `docs/VALIDATION.md`.
