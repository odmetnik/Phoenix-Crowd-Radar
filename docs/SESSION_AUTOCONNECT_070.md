# 0.7 — trajna sesija, automatsko povezivanje i AR udaljenosti

Podloga 0.7; nadogradnja s prosljeđivanjem opisana je u [RELAY_080.md](RELAY_080.md).

## Ekran više ne posjeduje radio

`RadarApp` posjeduje `RadarEngine` s Application kontekstom. U njemu su BLE/GATT, Nearby, Aware, GPS, UWB, članovi, poruke i fokus. Ne zadržava Activity, View, kameru ni callback ekrana. `MainActivity` čita stanje i šalje korisničke naredbe. Ponovno stvaranje ili odlazak ekrana ne gasi sesiju.

Korisnikov gumb pokreće `RadarService` tipova `connectedDevice|location` iz vidljive Activity nakon runtime dozvola. Servis ima trajnu obavijest s akcijom Zaustavi. GPS dijeljenje i dalje je zaseban izbor. Nema boot receivera, sticky ponovnog pokretanja, pozadinske kamere ili zahtjeva ACCESS_BACKGROUND_LOCATION. Servis i CPU wake lock završavaju najkasnije nakon šest sati. Trošak baterije, Doze/OEM ograničenja i fizička radio kompatibilnost zahtijevaju stvarni test. POST_NOTIFICATIONS nije uvjet za FGS; odbijanje gasi obične obavijesti, a korisnik i dalje može stati kroz aplikaciju/Android aktivne aplikacije.

BLE retry zadržava jedan nasumični session ID, kako se pri restartu skenera ne bi promijenile adrese poruka i UWB cilja. BLE pokušaj najranije svakih 15 s, dodatni neuspjeli/neaktivni Nearby/Aware pokušaji 20 s. Aktivna veza ne ruši se samo zato što nema BLE oglasa. `NumberPairing.stop()` više ne poziva Nearby stopAllEndpoints ako brojčano uparivanje nije stvarno bilo aktivno: uništenje neaktivnog dijaloga ne smije ubiti servisovu Nearby vezu.

## Što automatski pokušavamo

| Mehanizam | Otkrivanje / podaci | Udaljenost / smjer |
| --- | --- | --- |
| BLE + GATT | Grupni HMAC oglasi, do 6 GATT klijenata, 24 privremena člana | RSSI bez izmišljenih metara i smjera |
| Nearby P2P_POINT_TO_POINT | Jedan dodatni par; Google SDK bira BT/Wi-Fi | Ne proglašavamo Wi-Fi aktivnim bez API potvrde |
| Android Wi-Fi Aware | Samostalni publish/subscribe; kratki šifrirani podaci bez GMS-a ili routera; do 6 aktivnih razmjena | Sam Aware ne daje smjer |
| Wi-Fi RTT prema Aware članu | Automatski za fokus uz obostranu podršku i svježu Aware razmjenu | Uspješan izmjeren razmak; bez kuta; starost max 8 s |
| Jetpack UWB | Automatski za odabrani par uz obostranu podršku i dozvole | Stvarni razmak i opcionalni kut; max 3 s starosti |
| GPS + kompas | GPS dijeljenje ostaje opt-in; koordinate putem gornjih kanala | Približan razmak/smjer samo uz kvalitetne svježe podatke |

Nearby već upravlja svojim BT/Wi-Fi mehanizmima. Nismo dodali paralelnu zasebnu Wi-Fi Direct/Classic sesiju koja bi se otimala za iste radio resurse. Aware i drugi Wi-Fi načini mogu biti međusobno nedostupni na konkretnom čipu; `isAvailable()` i status to izričito pokazuju. Nema obećanja da svi rade istodobno ili da je domet povećan za određeni broj metara.

Aware service ID izveden je iz grupnog ključa, service-specific info je autentificirani Presence paket. Svaka publish sesija osvježava vremenski paket. Razmjena koristi svježi 128-bitni izazov, session ID-eve i postojeću Nearby AES-GCM domenu, različitu od GATT-a. `Fragments` ograničava datagrame na najviše 255 bajtova, cijeli ciphertext na 4096, broj fragmenata na 64 i sastavljanje na 6 s. Provjera grupnog ključa/autentičnosti obavlja se nad sastavljenim ciphertextom; sama fragment zaglavlja nisu autentifikacija. Red slanja ograničen je na 96 fragmenata. Profil, STATE i MAIL obrađuju se istim provjerenim aplikacijskim handlerom kao Nearby; poruke/PING dedupliciraju se po postojećem ID-u.

RTT request sadrži isključivo PeerHandle fokusiranog, svježe Aware autentificiranog člana; ne mjeri pristupne točke pa ih ne naziva prijateljima. Pokušaji najranije svake 4 s. Izvorni `getRangingTimestampMillis()` koristi se kao vrijeme mjerenja prema `elapsedRealtime()`, ne trenutak callbacka. Dopušta se samo STATUS_SUCCESS, uspješna mjerenja i nenegativan razmak. Standardna devijacija prikazuje se kao rasipanje rezultata, ne zajamčena točnost.

UWB automatski pokušava fokus do tri puta, min 30 s između početaka. STATE `f` javlja fokus; drugi član grupe može automatski prihvatiti ako nema svoj fokus. To je pristanak na razini već pridružene grupe, nije individualna fizička autentifikacija. Ručni pokušaj i zaustavljanje ostaju dostupni. Udaljenost: UWB → RTT → GPS → samo veza bez metara. GPS smjer se ne uzima ako se sukobljava s izravnim razmakom.

## Gumbi i upozorenja

POKRENI POVEZIVANJE pali servis i otkrivanje svih članova. ODABERI OSOBU bira fokus i automatske precizne pokušaje. Kada je sve isključeno, veliki gumb izričito kaže POKRENI I ODABERI OSOBU te čeka prve članove prije popisa. PROMIJENI OSOBU mijenja fokus.

Član se smatra svježim kad postoji svježi GATT STATE, Nearby odgovor, Aware odgovor ili još vrijedeći BLE oglas. GPS/UWB/RTT imaju svoje odvojene strože rokove. Nema održavanja stare točke samo zato što neki oglas stiže.

`LinkHealth` ne upozorava prije prvog kontakta. Nakon što svi putevi zastare, dodatnih 6 s bez veze daje jedno upozorenje za odabranog člana. Obnova mora biti stabilna 3 s prije potvrde povratka. Obavijest nije dokaz da se osoba fizički udaljila.

## AR

CameraX preview ostaje samo u vidljivom ekranu. HUD daje veliku strelicu, potpis fokusa i udaljenost; polukružni tlocrt s metarskim lukovima prilagođen je svježim razmacima. Valjani prednji kutovi određuju točke s imenima; osobe iza korisnika ne crtaju se ispred njega. Donja lista ispisuje imena, izvore, udaljenosti i smjerove po tri člana, s listanjem. Bez metara je — m; bez smjera nema strelice ili izmišljene točke.

Ovo je **shematski tlocrt preko kamere**, ne kalibrirana projekcija u videosliku. Bez vertikalnog kuta i potvrđenog odnosa antena–kamera nije moguće pošteno tvrditi da je natpis usidren na osobu. Prethodni pregled AR GitHub projekata ostaje u [NEARBY_AR.md](NEARBY_AR.md); sadašnji 0.7 kod koristi CameraX i vlastiti HUD.

## Provjera

JVM/Node provjere pokrivaju fragmentaciju/reassembly, drugi ključ, izmjenu paketa, rokove, nonce, adresiranje i deduplikaciju poruka, UWB/GPS pravila, AR smjer/skalu te različite UI akcije. Android CI gradi APK, radi lint i testira native onboarding, CameraX lifecycle, svježinu bez BLE oglasa, RTT prioritet/istek i korisnički pokrenut servis kroz pozadinu i ponovno stvaranje Activity.

Emulator ne potvrđuje stvarni Aware/RTT/UWB rad, veću udaljenost u gužvi ili rad svih proizvođačkih štednih režima. Prije terenske uporabe treba na dva telefona potvrditi: zaključavanje/otključavanje, poruke, gašenje/ponovno paljenje radija, udaljavanje i povratak, pokazanu tehnologiju te UWB/RTT razmak/smjer.
