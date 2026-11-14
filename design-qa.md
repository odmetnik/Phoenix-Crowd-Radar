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
