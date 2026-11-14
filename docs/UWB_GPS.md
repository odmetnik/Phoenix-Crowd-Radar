# Živo lociranje, protokol 3

Izvedba 0.3 koristi odvojena GATT svojstva za profil, živo stanje i izazov. Klijent prije ciklusa piše novi nasumični 16-bajtni izazov; oba šifrirana odgovora moraju nositi isti izazov, očekivani radio session ID i ispravan tip. AES-GCM sadržaj veže se uz grupni ključ. Stari odgovor prethodnog ciklusa nije valjan za novi izazov. Oglasi sami nisu podatkovni kanal za koordinate.

Server čuva stabilan snapshot pojedinog čitanja kroz GATT offsete i MTU. Profil se osvježava približno svakih 15 s; stanje odabranog peera oko 1 s, ostalih oko 2.5 s. Najviše šest aktivnih klijentskih veza; stvarna sposobnost adaptera može biti manja. Neuspjele veze zatvaraju se i ponovno pokušavaju nakon odgode.

## GNSS

GPS dijeljenje je zasebno uključivanje. Android GPS provider -> lokacija s monotonim timestampom -> šifrirano stanje -> udaljenost i smjer. Prijenos nosi starost očitanja, a primatelj konzervativno dodaje trajanje ciklusa i lokalno proteklo vrijeme. Time za svježinu GPS lokacije ne ovisimo o jednakom zidnom satu dvaju telefona. BLE presence prozor i dalje zahtijeva približno usklađene sistemske satove.

Za aktualno GPS očitanje dopušteno je do 10 s i razumna pozitivna accuracy. Za strelicu stroži uvjet traži da je razmak više nego dvostruki zbroj procjena pogreške, uz pojedinačne pogreške do 25 m. Procjene nisu zajamčene granice. Kompas mora biti svjež i pouzdan, s deklinacijom iz vlastite svježe lokacije.

## UWB

Oba telefona eksplicitno odabiru drugo i pripremaju Jetpack UWB scope. Poredak stabilnih lokalnih UUID-ova određuje kontroler/controlee. Oba objavljuju kratku ponudu namijenjenu radio session ID-u odabranog peera. Kontroler predlaže kanal, preamble, slučajan session ID i 16-bajtni nasumični ključ. Dogovor bira config 4 (provisioned unicast) kada ga oba podržavaju, inače config 1 (static unicast) s prvih 8 bajtova nasumičnog ključa. Nema nezaštićenog emitiranja ključa. Svi grupni članovi koji znaju grupni ključ ipak mogu čitati grupni kanal; nije implementirana zasebna pairwise povjerljivost.

Pripremljen scope ili pokrenuta sesija nije dokaz izmjerene pozicije. Samo svježi RangingResultPosition daje udaljenost i eventualni azimuth. Rezultati stariji od 3 s ili s nevaljanim vrijednostima ne ulaze u aktualni prikaz. Ne pretpostavljamo da svi modeli podržavaju kut. Ne pretvaramo pogrešku koja nije prijavljena u izmišljenu centimetarsku točnost.

Standardni azimuth je u odnosu na boresight antene; UI ga prikazuje samo pri uspravnom portretu. Radar nula tada znači iza leđa telefona, desno pozitivan kut. GPS kompas koristi istu naprijed referencu za uspravan telefon, a gornji rub za položen telefon. Točnost osi i predznaka treba potvrditi fizičkim testom za ciljne modele; nije provedena univerzalna kalibracija modela.

Prekid API-ja, nekompatibilne uloge, nedostupna postavka ili neuspješan dogovor daju objašnjenje i ostavljaju GPS/BLE. Automatsko prebacivanje uloga nakon neuspjeha još nije implementirano.

## Prikaz

Svjež UWB -> udaljenost UWB. Ako nema valjanog UWB kuta, dovoljno dobar GPS smjer može nadomjestiti kut samo kada se GPS i UWB razumno slažu. GPS smjer i UWB udaljenost tada imaju različite izričite oznake. Inače se crta krug bez kutne točke. BLE bez metara ne postavlja se na metarsku skalu.

## Obvezni terenski testovi

- S24 Ultra i drugi podržani UWB telefon, oba odabira, dopuštenja, uključen/isključen UWB, razmak 1/3/10 m, predznak kuta pri koraku lijevo/desno.
- Uspravan portret, položen i pejzažni telefon; u nepodržanom položaju nema UWB kutne točke.
- Jedan telefon bez UWB-a, nekompatibilan controller/controlee, prekid i ponovno pokretanje.
- GPS vani i u paviljonu, preklapanje pogreške, ugašeno dijeljenje, starost 3/10 s, loš kompas, neslaganje GPS/UWB.
- MTU 23 i 512, stabilni snapshoti, replay starog izazova, pogrešan ključ, 2 i 6 uređaja.
- 0.2 i 0.3 imaju različit živi protokol: za GPS/UWB svi trebaju 0.3 ili noviji kompatibilan protokol.
