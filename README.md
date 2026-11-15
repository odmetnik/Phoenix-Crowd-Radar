# Phoenix Crowd Radar — 0.8.0-dev

Android aplikacija za pronalaženje članova grupe: cyber radar, BLE blizina, GPS razmjena i UWB sesija za odabrani par. Phoenix Art / by Željko Kušter.

**0.8: build, lint, 184 provjere jezgre/protokola i svih deset Android testova prošli su u [GitHub Actions](https://github.com/odmetnik/Phoenix-Crowd-Radar/actions/runs/43709164991). Stvarni radio prijenos kroz tri telefona, domet i preciznost zahtijevaju fizički test.** Android 12+.

## Što radi 0.8

- TI → Marko → Ana: lokalno prosljeđivanje kroz najviše četiri veze, s oznakom puta i odvojenom svježinom krajnjeg odgovora.
- Krajnji metri samo iz dobrih svježih koordinata A i C, s konzervativno pribrojenim povratnim putem. Bez GPS-a nema izmišljene udaljenosti ni zbrajanja UWB/RTT dionica.
- Poruke i PING preko posrednika; dostavu potvrđuje krajnja aplikacija. Za posrednike treba 0.8 na svim uređajima na putu.
- Korisnički pokrenut foreground servis s obavijesti i Zaustavi; aktivna sesija i poruke preživljavaju zaključavanje i ponovno stvaranje ekrana. Nema kamere u pozadini, boot autostarta ili sticky restarta. Sesija najviše 6 h.
- Automatski ponovni BLE/Nearby/Aware pokušaji, odvojena svježina podataka i upozorenje na gubitak/povratak odabranog člana.
- Izravni šifrirani Wi-Fi Aware kanal bez GMS-a te pokušaji stvarnog RTT razmaka fokusiranog kompatibilnog Aware člana. RTT nije smjer; rad svih čipova treba fizički provjeriti.
- Odabir osobe pokušava UWB automatski (do tri pokušaja), uz ručnu kontrolu.
- AR velika strelica, imena, izvori, udaljenosti i prilagodljiva polukružna metarska shema. To nije 3D sidro na osobi.
- Dinamički AUTO/FOKUS zum, glatki prijelazi, metarske oznake i vidljivi bliski članovi. Bez smjera prikazujemo krug i upozorenje.
- Kratke Bluetooth poruke do 48 znakova s potvrdom primitka, zasebnim šifriranim kanalom i prolaznom poviješću. BLE je kompatibilan s 0.5+, dodatni Nearby BT/Wi-Fi kanal traži oba 0.6+.
- Lokalno RMS očitanje i nisko/povišeno linearno ubrzanje; mala vrijednost nije dokaz mirovanja. Ne računa položaj druge osobe.
- Dodatna Nearby P2P_POINT_TO_POINT veza za jedan par uz postojeći BLE. Profil, GPS, UWB parametri, PING i poruke rade preko oba puta. Stvarni medij bira Google SDK; ne prikazujemo izmišljeni Wi-Fi RSSI ili zajamčen domet.
- Eksperimentalni CameraX AR HUD: kamera uživo s imenima i shematskim azimutnim strelicama samo za svježe izmjerene smjerove. To nije kalibrirana 3D oznaka na osobi.
- Offline HTML/Canvas cyber sučelje prema referenci, animirani radar i odvojene kartice RADAR / GRUPA / PING.
- Jasno kreiranje/pridruživanje pri prvom pokretanju; QR i brojčana pozivnica nude kreiranje grupe ako nedostaje.
- Kreiranje grupe, promjenjiv naziv, korisnička imena i kartice mogućnosti telefona.
- QR pozivnica ili brojčana potvrda preko Google Nearby Connections. Kratki kod nije grupni ključ.
- BLE oglasi iste grupe daju RSSI razred, bez izmišljanja metara ili smjera.
- Živa šifrirana GATT veza: profili i stanje odvojeni su, svako čitanje vezano je uz nov nasumičan izazov. Do šest podatkovnih peerova; do 24 presence ID-a.
- GPS/GNSS prikupljanje preko Android GPS providera. Dijeljenje se uključuje zasebnim gumbom na svakom telefonu. Svježina i procjena pogreške putuju s lokacijom.
- Udaljenost i smjer iz koordinata samo kada su lokacije dovoljno dobre. Za smjer koristimo pouzdanu orijentaciju i deklinaciju prema pravom sjeveru. Previše nesiguran smjer je skriven.
- Jetpack UWB 1.0.0 za jednu odabranu osobu. Oba korisnika odabiru jedno drugo i pokreću UWB. Uloge su automatske, prednost ima zajednički provisioned STS, a zatim zajednički static STS profil. Random ključ i parametri sesije prenose se šifriranim grupnim GATT ili Nearby kanalom.
- UWB udaljenost i kut pojavljuju se tek nakon stvarnog svježeg API rezultata. UWB kut prikazuje se samo u podržanom uspravnom portretu; referenca je os antene iza stražnje strane telefona. Nije automatski GPS kompas. Test smjera konkretnog modela ostaje obvezan.
- Svjež UWB ima prednost za udaljenost, zatim valjan GPS, zatim BLE bez metara. GPS se ne koristi za smjer ako se značajno ne slaže s UWB udaljenošću. UWB zastarijeva nakon 3 s, GPS nakon 10 s.
- Pomoć je unutar aplikacije, uključuje sve tehnologije, ograničenja, privatnost i promjene verzije.

## Korištenje

1. Upiši svoje ime, kreiraj grupu i dodaj kolege QR-om ili brojčanom potvrdom.
2. Na oba telefona uključi Bluetooth, Wi-Fi i sistemsku Lokaciju, odobri dozvole i pokreni radar.
3. Za GPS na oba telefona zasebno uključi GPS dijeljenje i pričekaj dobar satelitski signal.
4. Za UWB na oba kompatibilna telefona uključi UWB u sustavu, dodirni kolegu i odaberi **Traži UWB**. Drži telefon uspravno u portretu. Oba odabira napravi unutar približno 25 s.
5. Pomoć objašnjava točke, krugove bez smjera, izvore, pogreške i domet.

Korisnički pokrenut servis nastavlja sesiju uz zaključan ekran i druge aplikacije; Stop zatvara GPS i sve veze. Android/OEM mogu ograničiti radio i UWB. GPS sam ne proširuje radijski domet. Postoji ograničeno aplikacijsko prosljeđivanje unutar grupe; nema internetskog servera za udaljeno praćenje. Zvučni PING odabranom članu šalje se lokalnom vezom; nema potvrde dostave. Aware i RTT implementirani su za kompatibilne članove, uz prikaz stvarne dostupnosti i uspješnog mjerenja. Nearby numeričko uparivanje koristi Bluetooth/Wi-Fi i Google Play usluge; ne treba internetski router. Google SDK ima vlastitu analitiku.

0.8 prosljeđivanje, GPS i zaštita od petlji: [docs/RELAY_080.md](docs/RELAY_080.md).

0.7 arhitektura, ograničenja i provjere: [docs/SESSION_AUTOCONNECT_070.md](docs/SESSION_AUTOCONNECT_070.md).

Povijesni detalji 0.6 veze i pregled AR projekata: [docs/NEARBY_AR.md](docs/NEARBY_AR.md).

## Help pri svakoj nadogradnji

`app/src/main/assets/help.txt` mora imati VERSION jednaku APK verziji i stvarni opis trenutačnog ponašanja. CI odbija promjenu izvora, manifesta, resursa ili konfiguracije aplikacije bez promjene Helpa u istom commitu. Android preBuild također provjerava verziju Helpa. Pravilo je trajno zapisano u AGENTS.md.

## Build

JDK 17, Gradle 8.11.1, Android SDK 36. Gradle wrapper nije uključen.

```bash
bash tests/run.sh
node tests/ui.mjs
python3 tests/check_help.py
gradle :app:assembleDebug :app:lintDebug
```

Repozitorij: https://github.com/odmetnik/Phoenix-Crowd-Radar . APK je u Artifacts uspješne izrade.

Od 0.3 koristimo stalan javni razvojni potpisni ključ (`signing/README.md`), namijenjen samo testnim verzijama. Prva 0.2 bila je potpisana prolaznim CI ključem: prelazak može zahtijevati uklanjanje stare aplikacije i ponovno pridruživanje grupi. Za produkciju treba zaseban privatni potpisni ključ.

## Provjere i privatnost

40 provjera čiste Java jezgre obuhvaća geometriju, pogreške, starost, neslaganje izvora, položaj antene, grupne pakete i šifriranje. Parser izvora nije zamjena za Android build. CI uključuje Android emulator smoke test spremanja grupnog ključa, QR dijaloga, uvoza pozivnice i početnog postupka. Fizička radio-kompatibilnost mora se provjeriti odvojeno. Vizualni prikaz provjeren je na snimci stvarnog Android WebViewa. Cloud pregled ostao je mrežno blokiran. Uzrok ranijih gubitaka fokusa potvrđen je snimkom: ANR dijalog Pixel Launchera prekrivao je aplikaciju. Testno okruženje sada privremeno izolira taj launcher; izrada 0.5.1 prošla je sva tri tadašnja Android UI testa; 0.6 dodaje provjere dodatne veze i CameraX HUD-a. Paket APK-a objavljuje se tek nakon prolaska builda, linta i testova jezgre/UI logike. Testovi fizičkog prijenosa poruka i senzora na dva telefona moraju se potvrditi odvojeno; vidi design-qa.md za ograničenja prethodne vizualne provjere.

Grupni ključ spremljen je pomoću Android Keystorea; sadržaj GATT-a šifriran AES-GCM-om. Koordinate i UWB ključevi sesije ne spremaju se u datoteke ili logove. Svaki član koji posjeduje grupni ključ ima pristup grupnim podacima. Prisutnost hardvera i imena/modeli iz profila nisu sigurnosna atestacija. Detalji su u Help-u i docs/UWB_GPS.md.
