# Provjera 0.7.0-dev — 15. 11. 2026. (Europe/Zagreb)

Produkcijski kod `fe0a03fc62c3c5c05e4843a6d10268e6ff68702a`; testovi/dokumentacija `a98afae40a755878c598c9b2d8d58cc80acba263`. Uspješni Actions runovi `43654822983` i završni `43655640431` (job `132633413728`). Build, lint, 40 CoreTest + 101 FeatureTest provjera, Node UI provjere i svih osam Android testova prolaze bez preskakanja.

Android test stvarno pokreće connectedDevice/location foreground servis iz vidljive Activity, spušta Activity u CREATED, ponovno je prikazuje i recreates; ista engine instanca, session ID i aktivni servis ostaju sačuvani. Zatim eksplicitni Stop gasi sesiju. Ovo potvrđuje životni ciklus na emulatoru, ne OEM štednju/Doze ili fizički radio na zaključanom Samsungu.

Native test provjerava RTT prioritet, izostanak izmišljenog smjera i istek razmaka nakon 8 s pomoću sintetičkog rezultata. Kamera test otvara, brzo zatvara i ponovno otvara CameraX preview, zatim oslobađa kameru. Snimka kamere može uhvatiti prethodni kadar; zato test uz nju sprema i `ar-grid.png`, sinkroni crtež stvarnog native HUD-a sa svježom sintetičkom UWB udaljenošću 3,2 m i kutom 25°. Assert provjerava vrijednost/smjer prije crtanja. To nije fizičko UWB mjerenje niti kalibracija kamere.

Vizualno pregledani završni dashboard i native AR grid: čitljivi odvojeni gumbi za povezivanje/odabir, postojeći cyber radar, AR velika strelica, smjer, izvor, udaljenost i metarski lukovi 1,3 / 2,5 / 3,8 / 5,0 m za radijus 5 m. Ugrađeni popis prikazuje članove s metrima i izričito — m kada nema mjerenja. Grid je shematski tlocrt, ne projekcija u videosliku. Testna imena su jasno označena QA.

Završni UI artefakt `14137774357`, APK artefakt `14137594262`. APK je bajt-po-bajt jednak prethodnom uspješnom runu: 17 465 572 bajta, SHA256 `2f36a21c3957fb683362a630c0954e6c3763de9cd95ef8197873f358c0c1a41f`. Integritet ZIP-a i zapakirani Help VERSION provjereni. APK v2 certifikat SHA256 `a7cb647ac2a3e41b1aca44a40c4fc1d9359cefa83908a2ecc4b26b3018dacc8c` jednak je ranijim razvojnim verzijama.

Preostalo fizički: interoperabilnost BLE/Nearby/Aware/RTT/UWB na konkretnim telefonima, prijenos poruka i ponovno povezivanje u gužvi, stvaran dobitak dometa i potrošnja, Samsungovi režimi štednje te UWB/camera poravnanje. Nijedno od toga nije proglašeno potvrđenim samim buildom ili emulatorom. Detalji u docs/SESSION_AUTOCONNECT_070.md i ažuriranoj ugrađenoj pomoći.

---

# Provjera 0.6.0-dev — 14. 11. 2026.

Produkcijski commit `45b7f393f2f89334afac62b0c6f111bbe351ae65`; GitHub Actions run `43645804894`, job `132603168191`: uspjeh. Android assembleDebug i lintDebug prošli. Prošlo 40 CoreTest + 78 FeatureTest provjera, Node testovi stvarnog UI skripta i svih šest Android testova (bez preskakanja).

Android emulator API 35 / Pixel 6: kreiranje/QR/uvoz, početni postupak bez grupe, zabrane Nearby/AR bez pokrenutog radara, svježina Nearby člana bez BLE oglasa i stvarno CameraX povezivanje/oslobađanje stražnje kamere s brzim zatvaranjem/ponovnim otvaranjem. CI izričito koristi emulirane kamere i izolira raniji Pixel Launcher ANR. Ispravljen je asinkroni Dialog onDismiss koji je mogao zatvoriti novu AR instancu.

Vizualno pregledane snimke `dashboard.png` i `ar-hud.png` iz uspješnog UI artefakta `14133802166`. Radar zadržava referentni cyber prikaz, dinamičku skalu, oznake članova i zasebnu stiliziranu AR kontrolu. AR HUD pokazuje ime sintetičkog testnog člana i izostanak izmjerenog smjera bez izmišljene strelice. Kamera je u testu povezana CameraX-om; emulirana pozadina snimke je crna i snimka nije dokaz stvarnog video sadržaja ni kalibracije na telefonu.

APK artefakt `14134386153`: ZIP SHA256 `d028f7e3b99c43e82771c1fccdb0a7fd3080292c183db073e1ece11d7e8387fa`. Izdvojeni APK ima 17 433 552 bajta, SHA256 `be9d58bc5fe6adc79872d77dbbdb2dd7faebb64b24f167bdea4c1ca48d0d5974`. Help VERSION i zapakirani novi UI provjereni u APK-u. Razvojni potpis ostaje isti kao ranije.

Neprovjereno fizičkim testom: veći domet, stvarni izbor Wi-Fi medija, BT/Wi-Fi koegzistencija, dostava između dva telefona i preciznost UWB/kamera poravnanja. Nearby pruža jedan dodatni par; nema Wi-Fi mesha ni 3D sidra osobe. AR je shematski smjer preko CameraX pregleda, ne projekcija položaja na sliku.

---

# Phoenix Crowd Radar 0.5.1 — završna provjera

- Produkcijski commit: `61275239fc249130623b079c8406433b37d0f16a`.
- Uspješna izrada: GitHub Actions run `43640579724`.
- Prošli su assembleDebug, lintDebug, 40 postojećih i 51 nova provjera jezgre te JavaScript testovi skale, fokusa, histereze, redoslijeda crtanja i HTML veza.
- Prošla su sva tri Android UI testa: stvaranje/pohrana grupe, QR prikaz i uvoz pozivnice; HTML gumb kroz Android most i snimka iscrtavanja; poseban početni dijalog radara.
- Pregledana je stvarna Android snimka `dashboard.png` iz UI artefakta 14131902219. Novi metarski prstenovi, točke, kartice i navigacija vidljivi su; simulirani podaci jasno su označeni. Oznake prstenova zaokružuju se radi čitljivosti.
- Stvarni JavaScript Canvas renderer zasebno je iscrtan s testnim udaljenostima 0,6 m i 0,01 m te s nepoznatim smjerom. Bliska oznaka nije prekrivena središtem, a nepoznat smjer daje prsten umjesto točke. Ovo nisu fizička mjerenja.
- Uzrok prijašnjih padova fokusa utvrđen je snimkom `focus-failure.png`: Pixel Launcher ANR dijalog prekrivao je aplikaciju. Privremeno isključivanje samo tog launchera unutar CI emulatora riješilo je blokadu; paket se nakon testova vraća. To nije postavka isporučenog APK-a.
- Potpis APK-a odgovara verzijama 0.3/0.4/0.5; potvrđeni su integritet ZIP-a, stvarni zapakirani izvori UI-ja i Help 0.5.1.
- Fizički Bluetooth prijenos poruka i potvrda između dva telefona, očitanje senzora na stvarnom mobitelu te UWB/GPS preciznost ove nadogradnje još nisu potvrđeni. AR nije implementiran.

final result: passed

Slijedi povijesna vizualna provjera verzije 0.4; njezina ograničenja odnose se na tadašnju izradu.

---

# Phoenix Crowd Radar 0.4 — Android visual QA

final result: passed

Scope: visual Android dashboard, with production controls and a taller phone aspect ratio. This is a close implementation of the supplied reference, not a claim of pixel identity or verified radio interoperability.

## Evidence

- Source visual truth: `/workspace/scratch/ef9dd40c8e2b/upload/01-1000172505.png`, 1024 × 1536.
- Implementation: `/workspace/scratch/ef9dd40c8e2b/Phoenix_Crowd_Radar_0.4.0_i.png`, 1080 × 2400, Android 15 Pixel 6 emulator, actual packaged WebView.
- Full-view comparison: `/workspace/scratch/ef9dd40c8e2b/qa-final-comparison.png`; both images normalized to 384 px width with original aspect ratios. Device chrome and the extra height of the phone are excluded from fidelity findings. CSS viewport/DPR were not separately measured.
- State: Interliber, three simulated members, UWB/GPS/BLE labels, animated sweep. Reference and capture show different animation phases.
- Screenshot came from successful workflow run 43613267644, production commit 458abfbe9bdfada4756b03cb918d26e0a4570f70. Subsequent changes affect only the emulator harness/tests.

## Findings and iterations

1. Cloud preview could not load because its upstream connection terminated. No claim of cloud-browser verification or inspected browser console.
2. First Android capture exposed system-bar overlap and captured an earlier compositor frame. Fixed with an inset FrameLayout, foreground readiness and WebView visual-state synchronization; the later demo capture shows clear system bars and the correct demo state.
3. Small-emulator capture exposed navigation below the fold. Fixed by keeping the navigation and author footer at the bottom and reserving scroll space. Pixel 6 capture confirms visible RADAR / GRUPA / PING controls. A compact short-screen rule is included; final compact layout was not separately recaptured after this change.
4. High-density capture exposed a weak sweep/glow and clipped card corner strokes. Increased sweep opacity, scaled canvas glow for density, and added continuous chamfered card edges. The final capture shows the sector, glowing dial and complete card borders.

## Required fidelity surfaces

- Typography: locally bundled SIL Orbitron closely matches the squared display heading. Body labels remain a readable system sans face. Header hierarchy and uppercase event labels match the reference. Exact original font is unidentified.
- Layout: circular degree dial, central arrow, numbered members, angular cards, prominent search action and bottom navigation follow the reference. The phone is taller; operational Start/Demo controls, truthful status, Help and profile access are intentional additions.
- Colors: dark blue-black, luminous cyan, green GPS member and amber unknown-direction member follow the reference. Glow and the sweep are visible in the final Android capture.
- Assets: locally bundled MIT Tabler icons; Canvas radar is code-native as requested. No rasterized mockup or remote assets replace the actual interface.
- Copy: Croatian, editable event name, explicit DEMO / KONCEPT and simulation warning. Actual BLE receives no invented meters or bearing. GPS is marked approximate.

Focused inspection: dial positions and labels, card borders/text, and the bottom navigation were inspected in the full-resolution implementation alongside the source and in the normalized composite. These regions are readable at source resolution; no further asset crop was required.

## Validation and remaining limits

- Successful delivery build: Android assembleDebug, lintDebug, 40 core checks, Node UI-state checks and all three Android enrollment/UI tests.
- The Android demo test clicks the HTML button and asserts that the native JavaScript bridge changes application state before capturing the committed frame.
- Native tests exercise encrypted group storage, QR generation/dialog, invitation import and the separate first-run radar dialog. They do not test camera scanning or Nearby exchange between two physical devices.
- Repeated CI runs have intermittent foreground-window-focus failures before interactions. Later harness readiness/unlock changes have not eliminated those failures; the cause is not confirmed. The delivered APK is from the successful run above, with identical production sources to current main.
- Cloud browser console inspection remains blocked; Android runtime rendering and bridge were exercised instead.
- BLE/GPS/UWB compatibility, numerical pairing and precision on two physical phones remain unverified.

## Follow-up polish (P3)

The reference has more ornamental circuit artwork and slightly different icons. GPS/BLE technology icons currently share the cyan tint, while numbered badges retain their semantic colors. Long BLE labels wrap on narrow cards. These differences do not block navigation or measurement interpretation.
