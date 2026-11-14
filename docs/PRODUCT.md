# Napomena o verziji

Ovaj dokument opisuje početnu arhitekturu i cilj. Za trenutačno stanje 0.3 mjerodavni su README.md i ugrađeni Help: GPS razmjena i UWB sesija sada su integrirane u izvor. Fizička kompatibilnost nije potvrđena; Wi-Fi RTT i dalje nije integriran. Donji tekst ostaje povijest početne arhitekture.

# Phoenix Crowd Radar — usvojeni smjer razvoja

## Problem i glavni tijek

Interliber, gužva, kolege na različitim mjestima. Prije razdvajanja jedna osoba kreira privremenu grupu za događaj; ostali skeniraju QR. Otvaranjem radara vide se samo članovi te grupe. Dodir osobe uključuje usmjereno traženje s najpouzdanijim dostupnim mjerenjem. Nema obveznog računa, servera ni pristupa sajamskoj Wi-Fi mreži.

## Što preuzimamo kao pristup

| Izvor | Korisna ideja | Naša dorada |
|---|---|---|
| Friendar | UWB + BLE + Wi-Fi Aware + GPS kao različiti izvori mjerenja | Provjera sposobnosti oba telefona; izvor i pouzdanost vidljivi korisniku |
| CrowdLink | QR grupa, lokalna komunikacija, šifrirane poruke, pronalaženje kolega | Privremene grupe za događaj; jednostavan tijek bez obveznog profila/telefonskog broja |
| Željkova ideja | Kružni radar za više uparenih telefona | Sve osobe u pregledu, jedna odabrana za precizno traženje |
| Phoenix NetWatch vizualni smjer | Cyber sučelje i Phoenix Art identitet | Fokus na čitljivost u hodu; bez izmišljenih koordinata i nepotrebnih tehničkih kontrola |

Kod projekata nije kopiran u ovu početnu izvedbu. Ovo je odabir arhitekturnih pristupa, ne dovršena integracija njihovih funkcija.

## Glavni ekran ciljne aplikacije

1. Zaglavlje: Phoenix Crowd Radar, naziv događaja/grupe, broj aktivnih članova, offline stanje.
2. Kružni radar: korisnik u središtu; rotacija prema smjeru držanja telefona kada postoji valjan kompas.
3. Član s izmjerenim kutom: obojena točka s imenom, mjerenom udaljenošću i oznakom izvora. Pojas nesigurnosti kada je podatak manje pouzdan.
4. Član bez smjera: signalni pojas po cijelom krugu i kartica ispod. Ne smije dobiti izmišljenu poziciju na obodu.
5. Odabrani član: veliki broj samo ako postoji opravdano mjerenje udaljenosti; strelica samo uz valjan smjer. Inače „signal jača/slabi” uz objašnjenje da to nije siguran dokaz približavanja.
6. Radnje: Grupa, Traži osobu, PING. PING treba potvrdu dostave; zahtjev uzrokuje vibraciju/zvuk samo prema postavkama primatelja.
7. Izgubljen član: sivo, zadnje viđen i vrijeme. Zadnja poznata pozicija nije aktualna pozicija.

## Pravila senzora

| Izvor | Što može dati | Kako prikazujemo |
|---|---|---|
| BLE RSSI | Grubu i promjenjivu jačinu/približnu blizinu | Signalni razred i trend; bez preciznih metara i bez smjera |
| Kompas | Orijentaciju vlastitog telefona | Rotacija valjanog smjera; nikad samostalna lokacija kolege |
| UWB | Udaljenost, a na podržanim kombinacijama i kut | Kut samo ako ga API stvarno vrati i mjerenje je svježe |
| Wi-Fi Aware / RTT | Otkrivanje/razmjenu i na podržanim uređajima udaljenost | Sama udaljenost ne daje strelicu; kombinirati s valjanim drugim izvorom |
| GPS/GNSS | Koordinate i procjenu pogreške | Izračun relativnog smjera samo uz svježe i dovoljno precizne koordinate oba uređaja |
| Žiroskop/akcelerometar | Orijentaciju i kratkoročno kretanje | Stabilizacija; ne obećavati preciznu neograničenu indoor navigaciju |

U paviljonu nema jamstva da običan telefon bez UWB-a može pokazati stvarnu strelicu do osobe. Ljudi, džep, metal i refleksije mijenjaju signal. Wi-Fi Direct je mogući transport podataka, ali sam ne rješava smjer.

UWB treba dogovor sesije preko sigurnog kanala. Za Android 14+ preferirati provisioned STS. Precizno traženje u početku raditi s jednom odabranom osobom; broj paralelnih sesija i uloge provjeravati na uređajima. Svi članovi ostaju na BLE pregledu.

GPS pravilo u početnoj jezgri konzervativno skriva smjer kada su pogreške velike u odnosu na razmak. Pragovi su proizvodna politika za testiranje, nisu specifikacija hardverske točnosti.

## Privatnost i protokol

Pozivnica sadrži nasumičan 256-bitni grupni ključ. Svaki posjednik ključa član je te grupe. To nije pojedinačno odobravanje članova niti dokaz identiteta. Za uklanjanje člana u početnom modelu svi preostali trebaju novu grupu.

Početni BLE presence sadrži slučajan 32-bitni session ID, 16-bitni vremenski slot i 48-bitni HMAC. Primarni oglas nosi grupni UUID izveden iz ključa, scan response 12 bajtova presence podataka. To stane u legacy BLE paket bez emitiranja imena ili koordinata. Tolerancija sata je ±1 slot po 30 sekundi. Ponavljanja oglasa su očekivana. Replay unutar prihvaćenog prozora i relay napad nisu riješeni. Grupni UUID može povezati aktivnosti iste grupe; koristiti novu grupu za novi događaj. Satovi telefona trebaju biti usklađeni.

Daljnji transport osobnih podataka: autentificirani handshake, odvojeni izvedeni ključevi za svrhe, AES-GCM s jedinstvenim nonce vrijednostima, session ID, brojač poruke i obrana od ponavljanja. Imena, koordinate i UWB parametri ne smiju otići neprovjerenom peeru. Ne prikazivati „end-to-end zaštićeno” dok to stvarno nije integrirano i provjereno.

## Mesh — kasnija faza

Poruke i koordinate mogu se prosljeđivati preko članova uz TTL, ID, deduplikaciju i ograničenje prometa. Član koji je udaljen tri skoka nije „blizu” samo zato što je signal posrednika jak. RSSI posrednika nikad ne prikazivati kao RSSI ciljne osobe. Sparse mesh ne jamči pokrivenost cijelog sajma; nema čarobnog dometa izvan svih radio-veza.

## Faze

- 0.1: cyber UI, jasno označen demo, grupni BLE presence, RSSI, kompas, trajno spremljen grupni ključ. Trenutačni izvorni projekt.
- 0.2: Android build i prvi test na dva uređaja, QR uparivanje, stabilni pseudonimi i autentificirani GATT transport.
- 0.3: GPS razmjena s vremenom i preciznošću, navigacija na otvorenom; PING s potvrdom.
- 0.4: UWB na dva kompatibilna telefona, npr. S24 Ultra i drugi podržani model; Wi-Fi Aware/RTT kao dodatni izvor udaljenosti.
- 0.5: upravljanje foreground servisom, štednja baterije i testiranje zaključanih zaslona na Samsungu.
- 0.6: više članova i selektivni mesh nakon provjerenog osnovnog transporta.

Naziv repozitorija predviđen za GitHub: `phoenix-crowd-radar`. Repozitorij: https://github.com/odmetnik/Phoenix-Crowd-Radar .
