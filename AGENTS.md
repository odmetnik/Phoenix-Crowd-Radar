# Phoenix Crowd Radar

The user requires Help to be updated with EVERY application upgrade.

- Keep `app/src/main/assets/help.txt` in Croatian. Explain actual technologies, controls, precision, limits, privacy and the current changelog.
- Match its VERSION header to `versionName` in `app/build.gradle.kts`.
- Any production app source, manifest, resource, or app/build.gradle.kts change must include an actual Help update in the same commit. `tests/check_help.py` enforces this in CI.
- Never describe detected hardware as an active measurement. Never invent BLE distance, UWB bearing, GPS precision or physical-device test results.
- Run `bash tests/run.sh`, `python3 tests/check_help.py`, Android assembleDebug and lintDebug. Radio interoperability must be tested on real phones and reported separately.
- Do not log or persist coordinates, group secrets or UWB session keys. Do not add background tracking by default.
