# Izvori, stanje 14. 11. 2026.

- Friendar: https://github.com/DanCommitsGit/friendar — README i arhitektura; UWB, BLE, Wi-Fi Aware, GPS. MIT. README izričito navodi da nema uparivanje ni šifriranje. Naš grupni model razvija se zasebno.
- CrowdLink: https://github.com/fionan313/CrowdLink — README; QR, šifrirane poruke, BLE, grupe, mesh i GPS. README navodi MIT. Naslov spominje Wi-Fi Direct, a glavni opis navodi BLE izvedbu; ne tretirati naslov kao dokaz aktivne Wi-Fi integracije.
- https://developer.android.com/develop/connectivity/uwb — UWB uloge, BLE OOB, provisioned STS, ograničenja u pozadini.
- https://developer.android.com/develop/connectivity/ranging — Android ranging i provjera sposobnosti.
- https://developer.android.com/develop/connectivity/wifi/wifi-rtt — Wi-Fi RTT, podržani AP i Wi-Fi Aware peerovi; udaljenost nije kut.
- https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback — GATT callbackovi za planirani transport.
- https://developer.android.com/build/releases/agp-8-9-0-release-notes — kompatibilnost AGP/Gradle/JDK.

Ovo nije audit sigurnosti navedenih projekata. Brojke o RSSI točnosti i dometu iz njihovih README datoteka nisu potvrđene u gužvi na Interliberu. Nisu preuzete kao jamstvo naše aplikacije.

U ovom paketu nema kopiranog koda Friendara ni CrowdLinka. Ako se kasnije preuzmu konkretni dijelovi, zadržati sve odgovarajuće MIT copyright i license notice datoteke. Licenca novog Phoenix Art koda zasad nije odabrana; ne dodjeljivati je automatski.

## Dodane ovisnosti 0.2

- ZXing Android Embedded 4.3.0, Apache 2.0: https://github.com/journeyapps/zxing-android-embedded — ScanContract i QR.
- Google Play services Nearby 19.5.0: https://developers.google.com/android/guides/releases — odabrana potvrđena verzija.
- https://developers.google.com/nearby/connections/android/manage-connections — usporedba autentifikacijskog koda prije prihvaćanja veze.
- https://developers.google.com/nearby/connections/android/get-started — dozvole i zahtjevi.
- https://android-developers.googleblog.com/2026/07/upcoming-changes-nearby-connections-api.html — korisnik mora ručno uključiti radio.
- https://developers.google.com/nearby/connections/overview — offline transport i Googleova SDK analitika.

Ovisnosti nisu vendorizirane, Gradle ih preuzima iz Google Maven/Maven Central. Njihove licence i uvjete treba zadržati u distribuciji. Vlastiti kod nema dodijeljenu javnu licencu.
