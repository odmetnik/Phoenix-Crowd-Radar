# Phoenix Crowd Radar — 0.5.0-dev

Android aplikacija za pronalaženje članova grupe: cyber radar, BLE blizina, GPS razmjena i UWB sesija za odabrani par. Phoenix Art / by Željko Kušter.

**Aktualna kompilacija i Android lint: GitHub Actions. Fizička UWB/BLE/GPS kompatibilnost i preciznost još nisu potvrđeni na telefonima.** APK je razvojni i zahtijeva Android 12+.

## Što radi 0.5

- Dinamički AUTO/FOKUS zum, glatki prijelazi, metarske oznake i vidljivi bliski članovi. Bez smjera prikazujemo krug i upozorenje.
- Kratke Bluetooth poruke do 48 znakova s potvrdom primitka, zasebnim šifriranim kanalom i prolaznom poviješću. Oba telefona trebaju 0.5+; Wi-Fi transport poruka nije ugrađen.
- Lokalna procjena kretanja iz linearnog ubrzanja ili filtriranog akcelerometra; ne računa položaj druge osobe.
- AR strelice i imena su sljedeća faza, još nisu ugrađeni.
- Offline HTML/Canvas cyber sučelje prema referenci, animirani radar i odvojene kartice RADAR / GRUPA / PING.
- Jasno kreiranje/pridruživanje pri prvom pokretanju; QR i brojčana pozivnica nude kreiranje grupe ako nedostaje.
- Kreiranje grupe, promjenjiv naziv, korisnička imena i kartice mogućnosti telefona.
- QR pozivnica ili brojčana potvrda preko Google Nearby Connections. Kratki kod nije grupni ključ.
- BLE oglasi iste grupe daju RSSI razred, bez izmišljanja metara ili smjera.
- Živa šifrirana GATT veza: profili i stanje odvojeni su, svako čitanje vezano je uz nov nasumičan izazov. Do šest podatkovnih peerova; do 24 presence ID-a.
- GPS/GNSS prikupljanje preko Android GPS providera. Dijeljenje se uključuje zasebnim gumbom na svakom telefonu. Svježina i procjena pogreške putuju s lokacijom.
- Udaljenost i smjer iz koordinata samo kada su lokacije dovoljno dobre. Za smjer koristimo pouzdanu orijentaciju i deklinaciju prema pravom sjeveru. Previše nesiguran smjer je skriven.
- Jetpack UWB 1.0.0 za jednu odabranu osobu. Oba korisnika odabiru jedno drugo i pokreću UWB. Uloge su automatske, prednost ima zajednički provisioned STS, a zatim zajednički static STS profil. Random ključ i parametri sesije prenose se šifriranim grupnim GATT kanalom.
- UWB udaljenost i kut pojavljuju se tek nakon stvarnog svježeg API rezultata. UWB kut prikazuje se samo u podržanom uspravnom portretu; referenca je os antene iza stražnje strane telefona. Nije automatski GPS kompas. Test smjera konkretnog modela ostaje obvezan.
- Svjež UWB ima prednost za udaljenost, zatim valjan GPS, zatim BLE bez metara. GPS se ne koristi za smjer ako se značajno ne slaže s UWB udaljenošću. UWB zastarijeva nakon 3 s, GPS nakon 10 s.
- Pomoć je unutar aplikacije, uključuje sve tehnologije, ograničenja, privatnost i promjene verzije.

## Korištenje

1. Upiši svoje ime, kreiraj grupu i dodaj kolege QR-om ili brojčanom potvrdom.
2. Na oba telefona uključi Bluetooth i sistemsku Lokaciju, odobri dozvole i pokreni radar.
3. Za GPS na oba telefona zasebno uključi GPS dijeljenje i pričekaj dobar satelitski signal.
4. Za UWB na oba kompatibilna telefona uključi UWB u sustavu, dodirni kolegu i odaberi **Traži UWB**. Drži telefon uspravno u portretu. Oba odabira napravi unutar približno 25 s.
5. Pomoć objašnjava točke, krugove bez smjera, izvore, pogreške i domet.

Aplikacija radi samo dok je otvorena. Zaključavanje/drugi app/Stop zaustavlja GPS, Bluetooth i UWB. GPS sam ne proširuje radijski domet. Nema mesha ni servera za udaljeno praćenje. Zvučni PING odabranom članu šalje se lokalnom vezom; nema potvrde dostave. Wi-Fi Aware/RTT su samo detekcija podrške; Wi-Fi RTT mjerenje još nije implementirano. Nearby numeričko uparivanje koristi Bluetooth/Wi-Fi i Google Play usluge; ne treba internetski router. Google SDK ima vlastitu analitiku.

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

40 provjera čiste Java jezgre obuhvaća geometriju, pogreške, starost, neslaganje izvora, položaj antene, grupne pakete i šifriranje. Parser izvora nije zamjena za Android build. CI uključuje Android emulator smoke test spremanja grupnog ključa, QR dijaloga, uvoza pozivnice i početnog postupka. Fizička radio-kompatibilnost mora se provjeriti odvojeno. Vizualni prikaz provjeren je na snimci stvarnog Android WebViewa. Cloud pregled ostao je mrežno blokiran. Ponovljeni CI UI testovi imaju nestabilan fokus prozora; uzrok još nije potvrđen. Paket APK-a objavljuje se tek nakon prolaska builda, linta i testova jezgre/UI logike. Testovi fizičkog prijenosa poruka i senzora na dva telefona moraju se potvrditi odvojeno; vidi design-qa.md za ograničenja prethodne vizualne provjere.

Grupni ključ spremljen je pomoću Android Keystorea; sadržaj GATT-a šifriran AES-GCM-om. Koordinate i UWB ključevi sesije ne spremaju se u datoteke ili logove. Svaki član koji posjeduje grupni ključ ima pristup grupnim podacima. Prisutnost hardvera i imena/modeli iz profila nisu sigurnosna atestacija. Detalji su u Help-u i docs/UWB_GPS.md.
