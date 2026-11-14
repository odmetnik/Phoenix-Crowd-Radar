# Kriteriji provjere prije dijeljenja APK-a

## Izvršivo u CI-ju

- Kompilacija jezgre i 17 provjera: pogrešna grupa, oštećen paket, istekao paket, rollover slota, geometrija, procjena nesigurnosti, smjer promjene RSSI-ja.
- Android assembleDebug i lintDebug.
- Provjera konačnog manifesta; nema internetske dozvole i nema background location dozvole.

## Obvezno na fizičkim uređajima

1. S24 Ultra + drugi Android 12+; oba otvorena, Bluetooth uključen, ista pozivnica. Detekcija obaju smjerova, RSSI i nestanak nakon zaustavljanja.
2. Različite pozivnice: nema članova druge grupe. Neispravna pozivnica odbijena. Ponovno otvaranje čuva grupu. Napuštanje briše lokalni ključ.
3. Odbijene/ukinute dozvole i isključen Bluetooth: bez rušenja, razumljiva poruka; ponovljeno pokretanje uspijeva.
4. Tijelo između uređaja, džep, metalni štand i velika gužva: zabilježiti signal i trend; usporediti s poznatim razmacima. Ne zaključivati točnost metara iz same jačine.
5. Stop, drugi app, zaključavanje: radio se zaustavlja u 0.1; ne prikazivati da zaštita od razdvajanja radi u pozadini.
6. Kompas: test u portretu i pejzažu, smetnje magneta; nepouzdan senzor mora ukloniti vrijednost. Kompas ne stvara smjer peer-a.
7. Clock skew: satovi unutar ±30 s; provjeriti granice slota i ponovno oglašavanje svakih 30 s.
8. Do 6 uređaja prije većeg skaliranja; mjeriti gubitke oglasa, trajanje baterije i stabilnost 30–60 min. Nema unaprijed obećanog radijskog dometa.
9. Mali ekran i Samsung s gestama: sve radnje dostupne skrolanjem, sadržaj iznad sistemske navigacije.

UWB/GPS/Wi-Fi testovi dodaju se tek kada njihove stvarne integracije postoje. Demonstracijski radar nije dokaz rada hardvera.

## Dodatni testovi za 0.2

- 24 lokalne provjere jezgre umjesto ranijih 17; parser svih Java datoteka nije Android build.
- QR generiranje, skeniranje i odustajanje; provjeriti dozvolu kamere i povratak u aplikaciju.
- Brojčano uparivanje na dva telefona: točan kod + odobrenje hosta; netočan kod; tri promašaja; odbijanje; istek 90 s; nema Google Play usluga; ugašen Wi-Fi/BT; ukinute Nearby dozvole.
- Potvrdu uspjeha host prikazuje tek nakon ACK-a drugog telefona. Provjeriti gubitak veze tijekom spremanja/ACK-a.
- Razmjena profila kroz GATT, MTU 23 i MTU 512, hrvatski znakovi, do 40 znakova imena/naziva, zastarjeli paket i pogrešan grupni ključ.
- Telefoni trebaju imati približno sinkronizirane satove; profil prihvaća odstupanje do 15 s. Testirati očekivane greške pri većem odstupanju.
- UWB na jednom telefonu ne smije prikazati zajednički UWB; UWB na oba ne smije prikazati aktivnu preciznost bez mjerenja.
- Promjena imena i naziva, ponovno otvaranje, ponovno pridruživanje drugoj grupi; stari naziv ne smije procuriti novim članovima pogrešne grupe.
- Nazivi se sinkroniziraju revizijom bez administratora; zasebne ovlasti/uklanjanje članova nisu implementirani.

## 0.3 — novi obvezni testovi

40 provjera jezgre, Help policy i puni Android build/lint. Fizički GPS/UWB/GATT testovi opisani su u UWB_GPS.md. Provjeriti i da GPS ne počinje bez zasebnog uključivanja, da Stop/onPause briše vlastiti fix i UWB ponudu te da se ni ključevi ni koordinate ne pojavljuju u CI/app logovima.
