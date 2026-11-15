# 0.8 — prosljeđivanje kroz članove grupe

Primjer: A vidi B, B vidi C, a A i C nemaju izravnu vezu. A može dobiti preneseno otkrivanje C, zatražiti njegovu svježu lokaciju te poslati poruku/PING preko B. Nema internetskog servera. Svaki susjedni par treba funkcionalan postojeći kanal; svi posrednici trebaju 0.8 i aktivnu sesiju.

## Prijenos i granice

`RelayRouter` je mali aplikacijski protokol preko BLE GATT-a, Nearbyja i Awarea, ne Bluetooth SIG Mesh. Novi opcionalni GATT MESH UUID završava `5635-8a65-4489-a390-b4ac6e79f821`; čita se poslije MAIL-a i ima isti nonce/session/AES-GCM okvir kao ostale karakteristike. Nearby/Aware bundle dodaje opcionalni `x`. Starije aplikacije ostaju izravni članovi i ne postaju posrednici.

Objave (`a`) nose samo UUID, ime i prijavljene mogućnosti izvora. Zahtjev (`q`) ima nasumični 96-bitni ID, krajnju sesiju i opcionalnu kratku poruku/PING. Odgovor (`r`) nosi izvorni ID zahtjeva, samo vlastiti GPS ako je dijeljenje uključeno i opcionalnu potvrdu krajnje poruke. Ne prosljeđuju se STATE, UWB ključevi/ponude, antenski kutovi, fokus ni RTT udaljenosti drugih parova.

Put uključuje stvarno prođene session ID-eve. Provjeravaju se početak/izvor, zadnji član naspram autentificiranog susjeda, izostanak ponavljanja i najviše četiri veze. Cache 512 ID-eva ograničava duplikate; petlje dodatno sprječava sam put. Do 24 rute, 48 izlaznih zapisa i 8 otvorenih zahtjeva. Žurni paketi imaju prednost, objave dobivaju svaku četvrtu priliku. Sve je u RAM-u. Stop prazni redove; nema spremanja ruta/koordinata u datoteke ili logove.

Body ima najviše 230 UTF-8 bajtova kako bi potpuni četverokoračni GATT okvir s najduljim signed ID-evima i nonceom ostao do 512 bajtova. Duga/escapirana imena se po potrebi skraćuju. Objave se obnavljaju svakih 6 s; lokalni red objava traje 16 s, zahtjeva/odgovora 8 s. Rute istječu 30 s nakon opažanja. Maksimum četiri veze nije jamstvo određenog broja metara niti rada svih čipova istodobno.

Fokus krajnjeg člana daje radio-prioritet prvom susjedu na putu. Novi zahtjevi stvaraju se najranije svake 2 s, uz postojeći otvoreni zahtjev najviše 20 s. Fokusirani član ima prednost; ostali se obilaze. Slanje poruke/PING-a zamjenjuje stari zahtjev svježim, s aktualnim sadržajem. Član koji samo prenosi poruku ne dodaje je u svoj inbox. Deduplikacija poruke ostaje po postojećem ID-u, a potvrda se pripisuje krajnjem izvoru, ne posredniku.

## Udaljenost krajnjeg telefona

**Ne zbrajamo A–B i B–C.** Jednake dvije dionice mogu dati različit razmak A–C. Smjer mrežne putanje nije smjer prema osobi.

Za svježe metre trebaju važeće koordinate A i C. A šalje novi izazov; prihvaća odgovor samo s očekivanom C sesijom i aktivnim ID-em, jednom, unutar 20 s. Ukupnoj starosti C GPS fixa dodaje cijeli izmjeren povratni put, uključujući čekanje u svim redovima. Kada zbroj prelazi 10 s, nema metara. Kopirani stari odgovor ne osvježava točku jer više nema otvorenog zahtjeva s tim ID-em. Nisu potrebni sinkronizirani satovi telefona.

Rezultat je `GPS preko mreže ~`: izračun ravnog površinskog razmaka iz krajnjih koordinata, s procjenama pogreške oba telefona. Smjer dodatno traži pouzdan kompas, deklinaciju i dovoljno dobre GPS podatke. To nije UWB mjerenje niti centimetarska preciznost.

Ako GPS u paviljonu ne radi ili ga C ne dijeli, mogu ostati poruke, PING i put TI → Marko → Ana, dok udaljenost ostaje nepoznata. UWB/RTT s posrednikom nikada se ne koristi kao razmak do C. UWB se ne pokreće samo zato što je C dostupan preko mreže. Kada C ponovno postane izravno dostupan, stvarni izravni izvori opet imaju prednost.

ČEKAM KRAJNJI ODGOVOR označava samo otkriven put bez odgovora C. Krajnji odgovor održava svježinu do 15 s, ali GPS/UWB/RTT imaju svoje zasebne strože rokove. Neuspjela ili nestala mrežna putanja ne održava stare koordinate aktualnima.

## Privatnost

Svi posrednici su članovi iste već odobrene grupe. Kanal je šifriran grupnim ključem; ovo nije individualno end-to-end šifriranje koje skriva sadržaj od drugih članova grupe niti kriptografska atestacija identiteta krajnjeg telefona. Imena, mogućnosti i lokaciju prijavljuje aplikacija. Ne dodaje se internet, server, mapa tuđih uređaja ili dijeljenje GPS-a bez postojećeg korisničkog uključivanja.

## Provjera

RelayTest simulira isti routing stroj kroz A–B–C: otkrivanje, upit/odgovor, put, duplikate, petlje, četiri veze, pogrešnog susjeda/izvora, istek, nonce i najgore Unicode/escape GATT pakete. Android provjera prolazi kroz stvarni šifrirani MESH read handler. Odvojena provjera tri engine instance obrađuje stvarne JSON pakete i potvrdu poruke od C, prazan inbox B, GPS razmak krajnjih koordinata te izostanak metara kada C isključi GPS. Sintetičko izravno UWB očitanje od 2 m ne smije se prikazati kao razmak do C.

Ti testovi nisu fizički RF test tri telefona. Potrebno je zasebno provjeriti stvaran prijenos preko B bez A–C veze, kašnjenje, gubitak/povratak posrednika, bateriju i GPS uvjete na lokaciji. U paviljonu nema obećanja o preciznom krajnjem razmaku bez valjanog mjerenja.
