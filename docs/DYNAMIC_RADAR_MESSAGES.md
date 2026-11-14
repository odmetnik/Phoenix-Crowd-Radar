# Phoenix Crowd Radar 0.5 — podloga

Nadogradnja 0.6 dodaje Nearby BT/Wi-Fi transport i eksperimentalni CameraX HUD: [NEARBY_AR.md](NEARBY_AR.md). Opis GATT-a niže odnosi se na osnovni kanal.

## Dinamički radar

`geometry.js` drži čistu geometriju i Zoom stanje. AUTO pokriva sve svježe metarske vrijednosti; odabrani izmjereni član dobiva FOKUS. Raspon je najmanje 0,5 m, bira se iz 1/2/5 niza s rezervom 25 %. Širenje je odmah pokrenuto, sužavanje traži stabilan kandidat 1,8 s; vizualna interpolacija ima vremensku konstantu 240 ms. Skala, prstenovi i pozicije računaju se istim trenutnim radijusom. BLE bez metara nikada ne određuje metarsku skalu.

Središte TI crta se prije članova. Vrlo bliske oznake imaju vodilicu do stvarne točke; tekst je pomaknut radi čitljivosti. Bez konačnog valjanog kuta nema točke. Udaljenosti izvan odabranog raspona označene su znakom > na rubu i tekstom na kartici.

## Bluetooth poruke

Opcionalna GATT karakteristika `abfa5634-8a65-4489-a390-b4ac6e79f821` nadograđuje v3 servis bez promjene postojećih profila, STATE ili CHALLENGE. Čita se nakon STATE samo ako ju je drugi uređaj oglasio. Svaki odgovor koristi postojeći nonce izazov, session ID i AES-GCM grupni ključ; MAIL ima zaseban cache koji se briše novim izazovom. Nema poruke u GPS/UWB STATE paketu.

MAIL sadrži najviše jednu odlaznu poruku (nasumičan 96-bit ID, ciljna presence sesija, najviše 48 UTF-16 jedinica teksta bez kontrolnih znakova) i zadnjih šest potvrda. Test pokriva najdulji negativni session ID, nonce, svih šest potvrda i Unicode/escape slučajeve: šifrirani okvir mora ostati ispod 512 bajtova.

Primitak je prihvaćen samo za lokalnu presence sesiju. Duplikati se potvrđuju bez ponovnog dodavanja; do 512 ID-eva zadržava se 120 s, duže od 60-sekundnog roka slanja. Puna deduplikacijska tablica odbija nove zahtjeve bez lažne potvrde. Potvrda mijenja stanje samo uz isti ID i istog ciljnog peera. Kasna valjana potvrda ima poseban status. Potvrda ne znači da je osoba pročitala tekst.

Povijest najviše 20 zapisa čuva se samo u memoriji. Stop prekida čekanje i briše transportno stanje; promjena grupe briše i povijest. Grupa dijeli ključ: adresiranje jednom članu nije zasebna kriptografska privatnost od ostalih članova.

## Akcelerometar

SENSOR_DELAY_UI: preferiran linearni senzor, inače obični akcelerometar s uklanjanjem gravitacije niskopropusnim filtrom. RMS/indikator niskog ili povišenog ubrzanja prikazuje se tek nakon zagrijavanja i dok su uzorci svježi. Ovo nije procjena brzine ili položaja; malo ubrzanje ne dokazuje da telefon miruje. ROTATION_VECTOR događaji obrađuju se odvojeno. Nema dodatnog praćenja u pozadini, prijenosa ili zapisivanja sirovih senzora.

## Kasniji AR

Od 0.6 ugrađen je shematski CameraX HUD, detalji u NEARBY_AR.md. Puni 3D AR još nije ugrađen. Prvi korak je overlay imena, udaljenosti i strelica uz valjan smjer i kalibriranu vezu antena–kamera. Potrebna je obrada vidnog polja, zakreta, kašnjenja i neizvjesnosti. AR praćenje vlastite kamere/telefona nije mjerenje kretanja druge osobe i ne može zamijeniti nedostajući UWB/GPS smjer.
