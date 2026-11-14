# Nearby + AR u 0.6.0 — povijesna podloga

Za aktualnu sesiju, Aware/RTT i AR mrežu vidi [SESSION_AUTOCONNECT_070.md](SESSION_AUTOCONNECT_070.md).

## Stvarna dodatna veza

BLE ostaje neovisan kanal za članove grupe (do šest GATT klijenata / 24 presence zapisa). `NearbyLink` uz njega pokreće Google Nearby Connections `P2P_POINT_TO_POINT`, jedan par s podrškom za BT/Wi-Fi transport kojim upravlja SDK. Nema pretpostavke da je Wi-Fi doista aktivan: javni callbackovi koje koristimo ne objavljuju fizički medij. Ne pokazujemo izmišljeni Wi-Fi RSSI, domet ili mrežnu topologiju.

Svaki telefon oglašava i traži servis izveden HMAC-om iz grupnog ključa. Endpoint name sadrži samo privremeni BLE session ID, ne ime osobe. Za izbjegavanje dvostrukih izlaznih zahtjeva inicira unsigned-manji session. Bez odabira automatski se prihvaća prvi slobodan par; odabir člana usmjerava nove veze prema tom članu. Oba trebaju odabrati jedno drugo ako su prethodno povezana s različitim članovima. Jedan zauzeti P2P par ne omogućuje spajanje trećega. Ne preusmjeravamo podatke između članova.

Nearbyjeva enkripcija nadopunjena je grupnim AES-GCM paketima u odvojenoj `nearby-v1` kriptografskoj domeni, različitoj od GATT-a. Autentificiran je član koji odgovori na novi 128-bitni izazov s očekivanim sender/recipient session ID-em i prihvatljivim profilom. Sam endpoint name nije autentifikacija. Grupa dijeli ključ, pa ne tvrdimo individualni identitet ili privatnost od ostalih članova.

Svake sekunde svaki telefon šalje novi izazov i dobiva trenutni profil, grupne metapodatke, STATE i MAIL. Paketi su ograničeni na 4096 bajtova; nonce vrijedi najviše 6 sekundi i prihvaća se jednom. Kašnjenje se konzervativno dodaje starosti GPS lokacije. Ne prenosimo udaljeni monotonički sat. Nakon 8 sekundi bez odgovora prekidamo i ponovno otkrivamo; neautentificiranu vezu prekidamo nakon 10 sekundi, pokušaj istog endpointa ponavljamo najranije nakon 15 sekundi.

Postojeći isti ID poruke i PING-a deduplicira se kroz oba puta. Potvrda poruke i dalje znači primitak u aplikaciji, ne čitanje. Sam prihvat Nearby transporta ili slanje payload-a nije potvrda dostave teksta. Profil/GPS/UWB/PING/MAIL putuju i GATT-om i Nearbyjem dok su oba dostupna. Gubitak BLE oglasa ne čini svjež Nearby par zastarjelim; nedostupan BLE RSSI nije prikazan kao izmjeren -127 dBm.

Ako Wi-Fi nije uključen, dozvola odbijena ili GMS nedostupan, BLE ostaje dostupan. PING kartica omogućuje ponovno pokretanje dodatne veze nakon uključivanja Wi-Fi-ja. Stop, odlazak iz aplikacije i promjena grupe zatvaraju sve kanale. Pozadina, poslužitelj i internet relay nisu dodani. Manifest INTERNET/CHANGE_NETWORK_STATE potreban je za lokalne IP veze SDK-a. Google SDK ima vlastitu analitiku.

BLE oglašavanje traži `ADVERTISE_TX_POWER_HIGH`, što je zahtjev sustavu, ne izmjerena izlazna snaga. Na gužvu, tijela i smetnje aplikacija ne može utjecati. Stvaran dobitak dometa i koegzistenciju radija treba izmjeriti na dva fizička telefona.

## Pregled GitHuba i izbor AR pristupa

Pregledano 14. studenoga 2026.:

| Projekt | Što je korisno | Odluka |
| --- | --- | --- |
| [ratana/rotation-vector-compass](https://github.com/ratana/rotation-vector-compass) (Apache-2.0) | Senzor rotacije, kamera i Canvas AR kompas | Koncept odgovara jednostavnom HUD-u; stariji proof of concept. Ne kopiramo njegov kod. |
| [sceneview/sceneview](https://github.com/sceneview/sceneview) | Filament/ARCore i 3D scena, noviji Compose pristup | Kandidat za kasnija sidra i 3D; znatno širi od trenutačnog Java/WebView projekta. Ne uvodimo veliki 3D sustav samo za azimutnu strelicu. |
| [google-ar/arcore-android-sdk](https://github.com/google-ar/arcore-android-sdk) | Službeni ARCore Android primjeri | Referenca za kasniji tracking kamere; ne mjeri ciljnu osobu i ne zamjenjuje UWB/GPS. |
| [android/connectivity-samples](https://github.com/android/connectivity-samples/tree/main/NearbyConnectionsWalkieTalkie) | Službeni Nearby lifecycle i payload primjeri | Koristimo postojeći Google Nearby SDK koji je već u aplikaciji; trajni kanal je prilagođen našoj grupnoj enkripciji i porukama. |

AR koristi AndroidX CameraX 1.4.2 (Preview, Camera2 i lifecycle), a ne kameru pisanu od nule. Canvas HUD je vlastiti kod. Nema preuzetih asseta ili izvornog koda tih GitHub projekata niti nove usluge/API ključa. Kamera ne snima i ne šalje sliku.

## Granice prve AR izvedbe

`ArOverlay` je fullscreen Dialog unutar iste Activity: radio i senzori nastavljaju raditi, kamera se oslobađa zatvaranjem. Zadnja kamera prikazuje se CameraX PreviewViewom, koji obrađuje rotaciju i omjer slike. Prikaz traži uspravan portret i svježu orijentaciju; druge položaje izričito odbija za strelicu.

Imena i udaljenosti dolaze iz istih provjerenih podataka kao radar. Strelica postoji samo uz svjež UWB kut ili dovoljno dobar GPS + kompas. AR bez kuta prikazuje nedostupnost; nema procjene iz BLE RSSI-ja ili akcelerometra. Traka azimuta je **shematska**, nije projekcija na piksele slike, procjena vidnog polja, visina osobe ili 3D sidro. Prednji smjer je stražnja strana telefona, isti kao postojeći uspravni radar. Iza korisnika prikazujemo OKRENI SE. Odabrani član dolazi prvi, ostatak se ograničava visinom zaslona.

Za pravu 3D oznaku u kadru treba potvrditi koordinatni sustav i kalibraciju antene/kamere konkretnih telefona te dodati dovoljno pouzdanu prostornu informaciju i kamerin tracking. ARCore ne rješava nedostajući smjer i vertikalni kut druge osobe.

## Provjera

Lokalni JVM testovi pokrivaju kriptografsku izolaciju, drugi grupni ključ, izmijenjeni/pretjerani paket, svježi nonce, ciljne session ID-eve, rok odgovora te AR prijelaz 359°/0°, lijevo/desno/iza i nedostajući kut. Node testovi provjeravaju stvarna HTML/native povezivanja. GitHub Actions radi Android build, lint i UI smoke testove. Fizički BT/Wi-Fi dobitak dometa, izmjena medija, kompatibilnost kamera i kalibracija UWB strelice nisu potvrđeni emulatorom.
