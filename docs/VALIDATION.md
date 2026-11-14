# GitHub provjera

Aktualni Android build i lint izvršavaju se u GitHub Actions: https://github.com/odmetnik/Phoenix-Crowd-Radar/actions . Prolazak izrade nije potvrda fizičkog BLE/QR/UWB rada.

## Povijest lokalne provjere prije GitHuba

# Provjera 14. 11. 2026. — 0.2.0-dev

- `bash tests/run.sh`: PASS, 24 core checks.
- Java jezgra kompilirana i izvršena na JDK 21: grupni presence, geometrija, nesigurnost, AES-GCM roundtrip, odbijanje pogrešnog ključa i promjene šifrata, jedinstveni nonce, zajedničke mogućnosti uređaja.
- Javac parser: svih 10 produkcijskih Java datoteka sintaktički valjano. Parser nije provjerio Android tipove ili ovisnosti.
- AndroidManifest.xml: uspješno XML parsiran.
- Android build/lint nisu izvršeni: nema Android SDK-a ni Gradlea u ovom okruženju.
- Nije potvrđena instalacija, QR kamera, Nearby autentifikacija/prijenos, GATT čitanje dugih vrijednosti, BLE oglašavanje ni stvarno UWB mjerenje.
- GitHub repozitorij nije stvoren i CI nije izvršen.

## Lokalna provjera 0.3

40 core checks prolazi; parser je obradio 13 produkcijskih Java datoteka. Help header i svi obvezni odjeljci prolaze. Puni Android build i fizički rad ovih izmjena provjeravaju se odvojeno.
